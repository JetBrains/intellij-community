// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

val OLD_NAME_CONVERTER: SchemeNameToFileName = object : SchemeNameToFileName {
  override fun schemeNameToFileName(name: String) = FileUtil.sanitizeFileName(name, true)
}
val CURRENT_NAME_CONVERTER: SchemeNameToFileName = object : SchemeNameToFileName {
  override fun schemeNameToFileName(name: String) = FileUtil.sanitizeFileName(name, false)
}
val MODERN_NAME_CONVERTER: SchemeNameToFileName = object : SchemeNameToFileName {
  override fun schemeNameToFileName(name: String) = sanitizeFileName(name)
}

interface SchemeDataHolder<in T> {
  /**
   * You should call updateDigest() after read on init.
   */
  fun read(): Element

  fun updateDigest(scheme: T)

  fun updateDigest(data: Element?)
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
interface SchemeContentChangedHandler<MUTABLE_SCHEME> {
  fun schemeContentChanged(scheme: MUTABLE_SCHEME, name: String, dataHolder: SchemeDataHolder<MUTABLE_SCHEME>)
}

abstract class LazySchemeProcessor<SCHEME, MUTABLE_SCHEME : SCHEME>(private val nameAttribute: String = "name") : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  open fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String? {
    return attributeProvider.apply(nameAttribute)
  }

  abstract fun createScheme(dataHolder: SchemeDataHolder<MUTABLE_SCHEME>,
                            name: String,
                            attributeProvider: Function<String, String?>,
                            isBundled: Boolean = false): MUTABLE_SCHEME
  override fun writeScheme(scheme: MUTABLE_SCHEME): Element? = (scheme as SerializableScheme).writeScheme()

  open fun isSchemeFile(name: CharSequence): Boolean = true

  open fun isSchemeDefault(scheme: MUTABLE_SCHEME, digest: ByteArray): Boolean = false

  open fun isSchemeEqualToBundled(scheme: MUTABLE_SCHEME): Boolean = false
}

class DigestOutputStream(val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun toString(): String = "[Digest Output Stream] $digest"
}

fun Element.digest(): ByteArray {
  // sha-1 is enough, sha-256 is slower, see https://www.nayuki.io/page/native-hash-functions-for-java
  val digest = MessageDigest.getInstance("SHA-1")
  serializeElementToBinary(this, DigestOutputStream(digest))
  return digest.digest()
}

abstract class SchemeWrapper<out T>(name: String) : ExternalizableSchemeAdapter(), SerializableScheme {
  protected abstract val lazyScheme: Lazy<T>

  val scheme: T
    get() = lazyScheme.value

  override fun getSchemeState(): SchemeState = if (lazyScheme.isInitialized()) SchemeState.POSSIBLY_CHANGED else SchemeState.UNCHANGED

  init {
    this.name = name
  }
}

abstract class LazySchemeWrapper<T>(name: String, dataHolder: SchemeDataHolder<SchemeWrapper<T>>, protected val writer: (scheme: T) -> Element) : SchemeWrapper<T>(name) {
  protected val dataHolder: AtomicReference<SchemeDataHolder<SchemeWrapper<T>>> = AtomicReference(dataHolder)

  override final fun writeScheme(): Element {
    val dataHolder = dataHolder.get()
    @Suppress("IfThenToElvis")
    return if (dataHolder == null) writer(scheme) else dataHolder.read()
  }
}

class InitializedSchemeWrapper<out T : Scheme>(scheme: T, private val writer: (scheme: T) -> Element) : SchemeWrapper<T>(scheme.name) {
  override val lazyScheme: Lazy<T> = lazyOf(scheme)

  override fun writeScheme(): Element = writer(scheme)
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