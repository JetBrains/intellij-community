// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.dynatrace.hash4j.hashing.HashStream64
import com.dynatrace.hash4j.hashing.Hashing
import com.intellij.openapi.options.*
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.io.FileUtil
import com.intellij.project.isDirectoryBased
import com.intellij.util.io.sanitizeFileName
import com.intellij.util.xmlb.annotations.Attribute
import org.jdom.CDATA
import org.jdom.Element
import org.jdom.Text
import org.jdom.Verifier
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
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

  fun updateDigest(scheme: T) {
  }

  fun updateDigest(data: Element?) {
  }
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
interface SchemeContentChangedHandler<MUTABLE_SCHEME: Scheme> {
  fun schemeContentChanged(scheme: MUTABLE_SCHEME, name: String, dataHolder: SchemeDataHolder<MUTABLE_SCHEME>)
}

abstract class LazySchemeProcessor<SCHEME : Scheme, MUTABLE_SCHEME : SCHEME>(private val nameAttribute: String = "name") : SchemeProcessor<SCHEME, MUTABLE_SCHEME>() {
  open fun getSchemeKey(attributeProvider: Function<String, String?>, fileNameWithoutExtension: String): String? {
    return attributeProvider.apply(nameAttribute)
  }

  abstract fun createScheme(dataHolder: SchemeDataHolder<MUTABLE_SCHEME>,
                            name: String,
                            attributeProvider: (String) -> String?,
                            isBundled: Boolean = false): MUTABLE_SCHEME

  override fun writeScheme(scheme: MUTABLE_SCHEME): Element? = (scheme as SerializableScheme).writeScheme()

  open fun isSchemeFile(name: CharSequence): Boolean = true

  open fun isSchemeDefault(scheme: MUTABLE_SCHEME, digest: Long): Boolean = false

  open fun isSchemeEqualToBundled(scheme: MUTABLE_SCHEME): Boolean = false
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

abstract class LazySchemeWrapper<T>(name: String,
                                    dataHolder: SchemeDataHolder<SchemeWrapper<T>>,
                                    protected val writer: (scheme: T) -> Element) : SchemeWrapper<T>(name) {
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
  if (JDOMUtil.isEmpty(element) || !project.isDirectoryBased) {
    element.name = "state"
    return element
  }

  val wrapper = Element("state")
  wrapper.addContent(element)
  return wrapper
}

class BundledSchemeEP {
  @Attribute("path")
  var path: String? = null
}

@ApiStatus.Internal
@TestOnly
val LISTEN_SCHEME_VFS_CHANGES_IN_TEST_MODE: Key<Boolean> = Key.create("LISTEN_VFS_CHANGES_IN_TEST_MODE")

@ApiStatus.Internal
fun hashElement(element: Element): Long {
  val hashStream = Hashing.komihash5_0().hashStream()
  hashElement(element, hashStream)
  return hashStream.asLong
}

@ApiStatus.Internal
fun hashElement(element: Element, hashStream: HashStream64) {
  hashStream.putByte(TypeMarker.ELEMENT.ordinal.toByte())
  // don't include length - node marker does the job
  hashStream.putChars(element.name)

  hashAttributes(if (element.hasAttributes()) element.attributes else null, hashStream)

  val content = element.content
  hashStream.putInt(content.size)
  for (item in content) {
    when (item) {
      is Element -> hashElement(item, hashStream)
      is CDATA -> {
        hashStream.putByte(TypeMarker.CDATA.ordinal.toByte())
        if (item.text == null) {
          hashStream.putInt(-1)
        }
        else {
          hashStream.putChars(item.text)
        }
      }
      is Text -> {
        val text = item.text
        if (text != null && !Verifier.isAllXMLWhitespace(text)) {
          hashStream.putByte(TypeMarker.TEXT.ordinal.toByte())
          hashStream.putChars(text)
        }
      }
    }
  }
}

private fun hashAttributes(attributes: List<org.jdom.Attribute>?, hashStream: HashStream64) {
  val size = attributes?.size ?: 0
  hashStream.putInt(size)
  if (size == 0) {
    return
  }

  for (attribute in attributes!!) {
    hashStream.putString(attribute.name)
    hashStream.putString(attribute.value)
  }
}