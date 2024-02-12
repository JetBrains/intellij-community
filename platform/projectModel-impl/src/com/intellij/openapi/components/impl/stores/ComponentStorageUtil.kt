// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.components.impl.stores

import com.intellij.application.options.PathMacrosCollector
import com.intellij.application.options.PathMacrosImpl.Companion.getInstanceEx
import com.intellij.openapi.components.CompositePathMacroFilter
import com.intellij.openapi.components.PathMacroSubstitutor
import com.intellij.openapi.components.TrackingPathMacroSubstitutor
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.text.StringUtilRt
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.util.LineSeparator
import com.intellij.util.SmartList
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.*
import java.util.*

private val LOG = Logger.getInstance(ComponentStorageUtil::class.java)

@ApiStatus.Internal
object ComponentStorageUtil {
  const val COMPONENT: String = "component"
  const val NAME: String = "name"
  const val DEFAULT_EXT: String = ".xml"

  fun load(rootElement: Element, pathMacroSubstitutor: PathMacroSubstitutor?): Map<String, Element> {
    pathMacroSubstitutor?.expandPaths(rootElement)

    var children = rootElement.getChildren(COMPONENT)
    if (children.isEmpty() && rootElement.name == COMPONENT && rootElement.getAttributeValue(NAME) != null) {
      // must be modifiable
      children = SmartList(rootElement)
    }

    val map = TreeMap<String, Element>()

    var filter: CompositePathMacroFilter? = null
    val iterator = children.iterator()
    while (iterator.hasNext()) {
      val element = iterator.next()
      val name = getComponentNameIfValid(element)
      if (name == null || (element.attributes.size <= 1 && element.content.isEmpty())) {
        continue
      }

      // so, PathMacroFilter can easily find component name (null parent)
      iterator.remove()

      if (pathMacroSubstitutor is TrackingPathMacroSubstitutor && !isKotlinSerializable(element)) {
        if (filter == null) {
          filter = CompositePathMacroFilter(PathMacrosCollector.MACRO_FILTER_EXTENSION_POINT_NAME.extensionList)
        }
        pathMacroSubstitutor.addUnknownMacros(name, PathMacrosCollector.getMacroNames(element, filter, getInstanceEx()))
      }

      // remove only after "getMacroNames" - some PathMacroFilter requires element name attribute
      element.removeAttribute(NAME)

      map[name] = element
    }

    return map
  }

  @Throws(IOException::class)
  fun load(
    dir: Path,
    pathMacroSubstitutor: PathMacroSubstitutor?
  ): Pair<Map<String, Element>, Map<String, LineSeparator?>> {
    try {
      Files.newDirectoryStream(dir).use { files ->
        val fileToState = HashMap<String, Element>()
        val fileToSeparator = HashMap<String, LineSeparator?>()

        for (file in files) {
          // ignore system files like .DS_Store on Mac
          if (!StringUtilRt.endsWithIgnoreCase(file.toString(), DEFAULT_EXT)) {
            continue
          }

          try {
            val elementLineSeparatorPair = loadDataAndDetectLineSeparator(Files.readAllBytes(file))
            val element = elementLineSeparatorPair.component1()
            val componentName = getComponentNameIfValid(element)
            if (componentName == null) continue

            if (element.name != COMPONENT) {
              LOG.error("Incorrect root tag name (" + element.name + ") in " + file)
              continue
            }

            val elementChildren = element.children
            if (elementChildren.isEmpty()) continue

            val state = elementChildren[0].detach()
            if (JDOMUtil.isEmpty(state)) {
              continue
            }

            if (pathMacroSubstitutor != null) {
              pathMacroSubstitutor.expandPaths(state)
              if (pathMacroSubstitutor is TrackingPathMacroSubstitutor) {
                pathMacroSubstitutor.addUnknownMacros(componentName, PathMacrosCollector.getMacroNames(state))
              }
            }

            val name = file.fileName.toString()
            fileToState[name] = state
            fileToSeparator[name] = elementLineSeparatorPair.component2()
          }
          catch (e: Throwable) {
            if (e.message!!.startsWith("Unexpected End-of-input in prolog")) {
              LOG.warn("Ignore empty file $file")
            }
            else {
              LOG.warn("Unable to load state from $file", e)
            }
          }
        }
        return Pair<Map<String, Element>, Map<String, LineSeparator?>>(fileToState, fileToSeparator)
      }
    }
    catch (e: DirectoryIteratorException) {
      throw e.cause!!
    }
    catch (ignore: NoSuchFileException) {
      return Pair(java.util.Map.of(), java.util.Map.of())
    }
    catch (ignore: NotDirectoryException) {
      return Pair(java.util.Map.of(), java.util.Map.of())
    }
  }
}

private fun isKotlinSerializable(element: Element): Boolean {
  if (element.hasAttributes()) return false
  val content = element.content
  return content.size == 1 && content[0] is Text
}

private fun getComponentNameIfValid(element: Element): String? {
  val name = element.getAttributeValue(ComponentStorageUtil.NAME)
  if (!(name == null || name.isEmpty())) {
    return name
  }
  LOG.warn("No name attribute for component in " + JDOMUtil.writeElement(element))
  return null
}

fun loadDataAndDetectLineSeparator(data: ByteArray): Pair<Element, LineSeparator?> {
  val offset = CharsetToolkit.getBOMLength(data, StandardCharsets.UTF_8)
  val text = String(data, offset, data.size - offset, StandardCharsets.UTF_8)
  val element = JDOMUtil.load(text)
  val lineSeparator = detectLineSeparator(text)
  return Pair(element, lineSeparator)
}

private fun detectLineSeparator(chars: CharSequence): LineSeparator? {
  for (element in chars) {
    if (element == '\r') {
      return LineSeparator.CRLF
    }
    // if we are here, there was no '\r' before
    if (element == '\n') {
      return LineSeparator.LF
    }
  }
  return null
}
