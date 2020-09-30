// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.configurationStore

import com.intellij.openapi.extensions.AbstractExtensionPointBean
import com.intellij.openapi.options.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.project.isDirectoryBased
import com.intellij.util.SmartList
import com.intellij.util.io.DigestUtil
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.isEmpty
import com.intellij.util.throwIfNotEmpty
import com.intellij.util.xmlb.annotations.Attribute
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.io.OutputStream
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Function

typealias SchemeNameToFileName = (name: String) -> String

val OLD_NAME_CONVERTER: SchemeNameToFileName = { FileUtil.sanitizeFileName(it, true) }
val CURRENT_NAME_CONVERTER: SchemeNameToFileName = { FileUtil.sanitizeFileName(it, false) }
val MODERN_NAME_CONVERTER: SchemeNameToFileName = { sanitizeFileName(it) }

interface SchemeDataHolder<in T> {
  /**
   * You should call updateDigest() after read on init.
   */
  fun read(): Element

  fun updateDigest(scheme: T) = Unit

  fun updateDigest(data: Element?) = Unit
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

abstract class LazySchemeProcessor<SCHEME : Any, MUTABLE_SCHEME : SCHEME>(private val nameAttribute: String = "name") : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  open fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String? {
    return attributeProvider.apply(nameAttribute)
  }

  abstract fun createScheme(dataHolder: SchemeDataHolder<MUTABLE_SCHEME>,
                            name: String,
                            attributeProvider: Function<in String, String?>,
                            isBundled: Boolean = false): MUTABLE_SCHEME
  override fun writeScheme(scheme: MUTABLE_SCHEME): Element? = (scheme as SerializableScheme).writeScheme()

  open fun isSchemeFile(name: CharSequence) = true

  open fun isSchemeDefault(scheme: MUTABLE_SCHEME, digest: ByteArray) = false

  open fun isSchemeEqualToBundled(scheme: MUTABLE_SCHEME) = false
}

private class DigestOutputStream(private val digest: MessageDigest) : OutputStream() {
  override fun write(b: Int) {
    digest.update(b.toByte())
  }

  override fun write(b: ByteArray, off: Int, len: Int) {
    digest.update(b, off, len)
  }

  override fun write(b: ByteArray) {
    digest.update(b)
  }

  override fun toString() = "[Digest Output Stream] $digest"

  fun digest(): ByteArray = digest.digest()
}

private val sha1MessageDigestThreadLocal = ThreadLocal.withInitial { DigestUtil.sha1() }

// sha-1 is enough, sha-256 is slower, see https://www.nayuki.io/page/native-hash-functions-for-java
fun createDataDigest(): MessageDigest {
  val digest = sha1MessageDigestThreadLocal.get()
  digest.reset()
  return digest
}

@JvmOverloads
fun Element.digest(messageDigest: MessageDigest = createDataDigest()): ByteArray {
  val digestOut = DigestOutputStream(messageDigest)
  serializeElementToBinary(this, digestOut)
  return digestOut.digest()
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

  final override fun writeScheme(): Element {
    val dataHolder = dataHolder.get()
    @Suppress("IfThenToElvis")
    return if (dataHolder == null) writer(scheme) else dataHolder.read()
  }
}

class InitializedSchemeWrapper<out T : Scheme>(scheme: T, private val writer: (scheme: T) -> Element) : SchemeWrapper<T>(scheme.name) {
  override val lazyScheme: Lazy<T> = lazyOf(scheme)

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
  throwIfNotEmpty(errors)
}

@ApiStatus.Internal
@TestOnly
val LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE = Key.create<Boolean>("LISTEN_VFS_CHANGES_IN_TEST_MODE")