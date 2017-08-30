/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.configurationStore

import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.project.isDirectoryBased
import com.intellij.util.SmartList
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.isEmpty
import com.intellij.util.lang.CompoundRuntimeException
import com.intellij.util.xmlb.annotations.Attribute
import org.jdom.Element
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

interface SchemeNameToFileName {
  fun schemeNameToFileName(name: String): String
}

val OLD_NAME_CONVERTER = object : SchemeNameToFileName {
  override fun schemeNameToFileName(name: String) = FileUtil.sanitizeFileName(name, true)
}
val CURRENT_NAME_CONVERTER = object : SchemeNameToFileName {
  override fun schemeNameToFileName(name: String) = FileUtil.sanitizeFileName(name, false)
}
val MODERN_NAME_CONVERTER = object : SchemeNameToFileName {
  override fun schemeNameToFileName(name: String) = sanitizeFileName(name)
}

interface SchemeDataHolder<in T : Scheme> {
  /**
   * You should call updateDigest() after read on init.
   */
  fun read(): Element

  fun updateDigest(scheme: T)

  fun updateDigest(data: Element)
}

/**
 * A scheme processor can implement this interface to provide a file extension different from default .xml.
 * @see SchemeProcessor
 */
interface SchemeExtensionProvider {
  /**
   * @return The scheme file extension **with a leading dot**, for example ".ext".
   */
  val schemeExtension: String
}

// applicable only for LazySchemeProcessor
interface SchemeContentChangedHandler<MUTABLE_SCHEME : Scheme> {
  fun schemeContentChanged(scheme: MUTABLE_SCHEME, name: String, dataHolder: SchemeDataHolder<MUTABLE_SCHEME>)
}

abstract class LazySchemeProcessor<SCHEME : Scheme, MUTABLE_SCHEME : SCHEME>(private val nameAttribute: String = "name") : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  open fun getName(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String {
    return attributeProvider.apply(nameAttribute)
           ?: throw IllegalStateException("name is missed in the scheme data")
  }

  abstract fun createScheme(dataHolder: SchemeDataHolder<MUTABLE_SCHEME>,
                            name: String,
                            attributeProvider: Function<String, String?>,
                            isBundled: Boolean = false): MUTABLE_SCHEME
  override fun writeScheme(scheme: MUTABLE_SCHEME) = (scheme as SerializableScheme).writeScheme()

  open fun isSchemeFile(name: CharSequence) = true

  open fun isSchemeDefault(scheme: MUTABLE_SCHEME, digest: ByteArray) = false

  open fun isSchemeEqualToBundled(scheme: MUTABLE_SCHEME) = false
}

class DigestOutputStream(val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun toString() = "[Digest Output Stream] $digest"
}

fun Element.digest(): ByteArray {
  // sha-1 is enough, sha-256 is slower, see https://www.nayuki.io/page/native-hash-functions-for-java
  val digest = MessageDigest.getInstance("SHA-1")
  serializeElementToBinary(this, DigestOutputStream(digest))
  return digest.digest()
}

abstract class SchemeWrapper<out T : Scheme>(name: String) : ExternalizableSchemeAdapter(), SerializableScheme {
  protected abstract val lazyScheme: Lazy<T>

  val scheme: T
    get() = lazyScheme.value

  override fun getSchemeState() = if (lazyScheme.isInitialized()) SchemeState.POSSIBLY_CHANGED else SchemeState.UNCHANGED

  init {
    this.name = name
  }
}

abstract class LazySchemeWrapper<T : Scheme>(name: String, dataHolder: SchemeDataHolder<SchemeWrapper<T>>, protected val writer: (scheme: T) -> Element) : SchemeWrapper<T>(name) {
  protected val dataHolder = AtomicReference(dataHolder)

  override final fun writeScheme(): Element {
    val dataHolder = dataHolder.get()
    @Suppress("IfThenToElvis")
    return if (dataHolder == null) writer(scheme) else dataHolder.read()
  }
}

class InitializedSchemeWrapper<out T : Scheme>(scheme: T, private val writer: (scheme: T) -> Element) : SchemeWrapper<T>(scheme.name) {
  override val lazyScheme = lazyOf(scheme)

  override fun writeScheme() = writer(scheme)
}

fun unwrapState(element: Element, project: Project, iprAdapter: SchemeManagerIprProvider?, schemeManager: SchemeManager<*>): Element? {
  val data = if (project.isDirectoryBased) element.getChild("settings") else element
  iprAdapter?.let {
    it.load(data)
    schemeManager.reload()
  }
  return data
}

fun wrapState(element: Element, project: Project): Element {
  if (element.isEmpty() || !project.isDirectoryBased) {
    element.name = "state"
    return element
  }

  val wrapper = Element("state")
  wrapper.addContent(element)
  return wrapper
}

class BundledSchemeEP : AbstractExtensionPointBean() {
  @Attribute("path")
  var path: String? = null
}

fun SchemeManager<*>.save() {
  val errors = SmartList<Throwable>()
  save(errors)
  CompoundRuntimeException.throwIfNotEmpty(errors)
}