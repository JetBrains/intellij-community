// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.application.options.ReplacePathToMacroMap
import com.intellij.openapi.application.PathMacroFilter
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.PathMacroManager
import com.intellij.openapi.components.impl.stores.ComponentStorageUtil
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.JDOMUtil
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.io.FileUtilRt
import com.intellij.util.SystemProperties
import com.intellij.util.xmlb.Constants
import org.jdom.*
import org.jdom.output.Format
import java.io.IOException
import java.io.StringWriter
import java.io.Writer
import java.util.*

// expandEmptyElements is ignored
open class JbXmlOutputter @JvmOverloads constructor(lineSeparator: String = "\n",
                                                    private val elementFilter: JDOMUtil.ElementOutputFilter? = null,
                                                    private val macroMap: ReplacePathToMacroMap? = null,
                                                    private val macroFilter: PathMacroFilter? = null,
                                                    private val isForbidSensitiveData: Boolean = true,
                                                    private val storageFilePathForDebugPurposes: String? = null) : BaseXmlOutputter(lineSeparator) {
  companion object {
    @Throws(IOException::class)
    fun collapseMacrosAndWrite(element: Element, project: ComponentManager, writer: Writer) {
      createOutputter(project).output(element, writer)
    }

    fun createOutputter(project: ComponentManager): JbXmlOutputter {
      val macroManager = PathMacroManager.getInstance(project)
      return JbXmlOutputter(macroMap = macroManager.replacePathMap, macroFilter = macroManager.macroFilter)
    }

    @JvmStatic
    @Throws(IOException::class)
    fun collapseMacrosAndWrite(element: Element, project: ComponentManager): String {
      val writer = StringWriter()
      collapseMacrosAndWrite(element = element, project = project, writer = writer)
      return writer.toString()
    }

    fun escapeElementEntities(str: String?): String {
      return JDOMUtil.escapeText(str!!, false, false)
    }

    private val reportedSensitiveProblems = Collections.synchronizedSet(HashSet<String>())
  }

  private val format = JDOMUtil.createFormat(lineSeparator)

  @Throws(IOException::class)
  fun output(doc: Document, out: Writer) {
    printDeclaration(out, format.encoding)

    // Print out root element, as well as any root level comments and processing instructions, starting with no indentation
    val content = doc.content
    for (obj in content) {
      when (obj) {
        is Element -> printElement(out, doc.rootElement, 0)
        is DocType -> {
          printDocType(out, doc.docType)
          // Always print line separator after declaration, helps the
          // output look better and is semantically inconsequential
          writeLineSeparator(out)
        }
      }

      newline(out)
      indent(out, 0)
    }

    writeLineSeparator(out)

    out.flush()
  }

  @Throws(IOException::class)
  private fun writeLineSeparator(out: Writer) {
    if (format.lineSeparator != null) {
      out.write(format.lineSeparator)
    }
  }

  /**
   * Print out the `[DocType]`.
   *
   * @param doctype `DocType` to output.
   * @param out     `Writer` to use.
   */
  @Throws(IOException::class)
  fun output(doctype: DocType, out: Writer) {
    printDocType(out, doctype)
    out.flush()
  }

  @Throws(IOException::class)
  fun output(element: Element, out: Writer) {
    printElement(out, element, 0)
  }

  /**
   * This will handle the printing of the declaration.
   * Assumes XML version 1.0 since we don't directly know.
   *
   * @param out      `Writer` to use.
   * @param encoding The encoding to add to the declaration
   */
  @Throws(IOException::class)
  private fun printDeclaration(out: Writer, encoding: String) {
    // Only print the declaration if it's not being omitted
    if (!format.omitDeclaration) {
      // Assume 1.0 version
      out.write("<?xml version=\"1.0\"")
      if (!format.omitEncoding) {
        out.write(" encoding=\"$encoding\"")
      }
      out.write("?>")
      writeLineSeparator(out)
    }
  }

  /**
   * This will handle printing of `[CDATA]` text.
   *
   * @param cdata `CDATA` to output.
   * @param out   `Writer` to use.
   */
  @Throws(IOException::class)
  private fun printCDATA(out: Writer, cdata: CDATA) {
    var str: String
    if (format.textMode == Format.TextMode.NORMALIZE) {
      str = cdata.textNormalize
    }
    else {
      str = cdata.text
      if (format.textMode == Format.TextMode.TRIM) {
        str = str.trim()
      }
    }
    out.write("<![CDATA[")
    out.write(str)
    out.write("]]>")
  }

  /**
   * This will handle printing a string.  Escapes the element entities,
   * trims interior whitespace, etc. if necessary.
   */
  @Throws(IOException::class)
  private fun printString(out: Writer, str: String) {
    var normalizedString = str
    if (format.textMode == Format.TextMode.NORMALIZE) {
      normalizedString = Text.normalizeString(normalizedString)
    }
    else if (format.textMode == Format.TextMode.TRIM) {
      normalizedString = normalizedString.trim()
    }

    if (macroMap != null) {
      normalizedString = macroMap.substitute(normalizedString, SystemInfoRt.isFileSystemCaseSensitive)
    }
    out.write(escapeElementEntities(normalizedString))
  }

  /**
   * This will handle printing of a `[Element]`,
   * its `[Attribute]`s, and all contained (child)
   * elements, etc.
   *
   * @param element `Element` to output.
   * @param out     `Writer` to use.
   * @param level   `int` level of indention.
   */
  @Throws(IOException::class)
  fun printElement(out: Writer, element: Element, level: Int) {
    printElementImpl(out, element, level, macroFilter != null)
  }

  @Throws(IOException::class)
  private fun printElementImpl(out: Writer, element: Element, level: Int, substituteMacro: Boolean) {
    if (elementFilter != null && !elementFilter.accept(element, level)) {
      return
    }
    val currentSubstituteMacro = substituteMacro && (macroFilter != null && !macroFilter.skipPathMacros(element))

    // Print the beginning of the tag plus attributes and any
    // necessary namespace declarations
    out.write('<'.code)
    printQualifiedName(out, element)

    if (element.hasAttributes()) {
      printAttributes(out, element.attributes, currentSubstituteMacro)
    }

    // depending on the settings (newlines, textNormalize, etc.), we may or may not want to print all the content,
    // so determine the index of the start of the content we're interested in based on the current settings.
    if (!writeContent(out, element, level, currentSubstituteMacro)) {
      return
    }

    out.write("</")
    printQualifiedName(out, element)
    out.write('>'.code)
  }

  @Throws(IOException::class)
  protected open fun writeContent(out: Writer, element: Element, level: Int, substituteMacro: Boolean): Boolean {
    if (isForbidSensitiveData) {
      checkIsElementContainsSensitiveInformation(element)
    }

    val content = element.content
    val start = skipLeadingWhite(content, 0)
    val size = content.size
    if (start >= size) {
      // content is empty or all insignificant whitespace
      out.write(" />")
      return false
    }

    out.write('>'.code)

    // for a special case where the content is only CDATA or Text we don't want to indent after the start or before the end tag
    if (nextNonText(content, start) < size) {
      // case Mixed Content - normal indentation
      newline(out)
      printContentRange(out, content, start, size, level + 1, substituteMacro)
      newline(out)
      indent(out, level)
    }
    else {
      // case all CDATA or Text - no indentation
      printTextRange(out, content, start, size)
    }
    return true
  }

  /**
   * This will handle printing of content within a given range.
   * The range to print is specified in typical Java fashion; the
   * starting index is inclusive, while the ending index is
   * exclusive.
   *
   * @param content `List` of content to output
   * @param start   index of first content node (inclusive.
   * @param end     index of last content node (exclusive).
   * @param out     `Writer` to use.
   * @param level   `int` level of indentation.
   */
  @Throws(IOException::class)
  private fun printContentRange(out: Writer, content: List<Content>, start: Int, end: Int, level: Int, substituteMacro: Boolean) {
    var firstNode: Boolean // Flag for 1st node in content
    var next: Content       // Node we're about to print
    var first: Int
    var index: Int  // Indexes into the list of content

    index = start
    while (index < end) {
      firstNode = index == start
      next = content[index]

      // Handle consecutive CDATA, Text, and EntityRef nodes all at once
      if (next is Text || next is EntityRef) {
        first = skipLeadingWhite(content, index)
        // Set index to next node for loop
        index = nextNonText(content, first)

        // If it's not all whitespace - print it!
        if (first < index) {
          if (!firstNode) {
            newline(out)
          }
          indent(out, level)
          printTextRange(out, content, first, index)
        }
        continue
      }

      // Handle other nodes
      if (!firstNode) {
        newline(out)
      }

      indent(out, level)

      if (next is Element) {
        printElementImpl(out, next, level, substituteMacro)
      }

      index++
    }
  }

  /**
   * This will handle printing of a sequence of `[CDATA]`
   * or `[Text]` nodes.  It is an error to have any other
   * pass this method any other type of node.
   *
   * @param content `List` of content to output
   * @param start   index of first content node (inclusive).
   * @param end     index of last content node (exclusive).
   * @param out     `Writer` to use.
   */
  @Throws(IOException::class)
  private fun printTextRange(out: Writer, content: List<Content>, start: Int, end: Int) {
    @Suppress("NAME_SHADOWING")
    val start = skipLeadingWhite(content, start)
    if (start >= content.size) {
      return
    }

    // and remove trialing whitespace-only nodes
    @Suppress("NAME_SHADOWING")
    val end = skipTrailingWhite(content, end)

    var previous: String? = null
    for (i in start until end) {
      val node = content[i]

      // get the unmangled version of the text we are about to print
      val next: String?
      when (node) {
        is Text -> next = node.text
        is EntityRef -> next = "&" + node.getValue() + ";"
        else -> throw IllegalStateException("Should see only CDATA, Text, or EntityRef")
      }

      if (next.isNullOrEmpty()) {
        continue
      }

      // determine if we need to pad the output (padding is only need in trim or normalizing mode)
      if (previous != null && (format.textMode == Format.TextMode.NORMALIZE || format.textMode == Format.TextMode.TRIM)) {
        if (endsWithWhite(previous) || startsWithWhite(next)) {
          out.write(' '.code)
        }
      }

      // print the node
      when (node) {
        is CDATA -> printCDATA(out, node)
        is EntityRef -> printEntityRef(out, node)
        else -> printString(out, next)
      }

      previous = next
    }
  }

  /**
   * This will handle printing of a `[Attribute]` list.
   *
   * @param attributes `List` of Attribute objects
   * @param out        `Writer` to use
   */
  @Throws(IOException::class)
  private fun printAttributes(out: Writer, attributes: List<Attribute>, substituteMacro: Boolean) {
    for (attribute in attributes) {
      out.write(' '.code)
      printQualifiedName(out, attribute)
      out.write('='.code)
      out.write('"'.code)

      val value = if (macroMap != null && substituteMacro && (macroFilter == null || !macroFilter.skipPathMacros(attribute))) {
        macroMap.getAttributeValue(attribute, macroFilter, SystemInfoRt.isFileSystemCaseSensitive, false)
      }
      else {
        attribute.value
      }

      if (isForbidSensitiveData && doesNameSuggestSensitiveInformation(attribute.name)) {
        logSensitiveInformationError("@${attribute.name}", "Attribute", attribute.parent)
      }

      out.write(escapeAttributeEntities(value))
      out.write('"'.code)
    }
  }

  /**
   * This will print a newline only if indent is not null.
   *
   * @param out `Writer` to use
   */
  @Throws(IOException::class)
  private fun newline(out: Writer) {
    if (format.indent != null) {
      writeLineSeparator(out)
    }
  }

  /**
   * This will print indents only if indent is not null or the empty string.
   *
   * @param out   `Writer` to use
   * @param level current indent level
   */
  @Throws(IOException::class)
  private fun indent(out: Writer, level: Int) {
    if (format.indent.isNullOrEmpty()) {
      return
    }

    for (i in 0 until level) {
      out.write(format.indent)
    }
  }

  // Returns the index of the first non-all-whitespace CDATA or Text,
  // index = content.size() is returned if content contains
  // all whitespace.
  // @param start index to begin search (inclusive)
  private fun skipLeadingWhite(content: List<Content>, start: Int): Int {
    var index = start
    if (index < 0) {
      index = 0
    }

    val size = content.size
    val textMode = format.textMode
    if (textMode == Format.TextMode.TRIM_FULL_WHITE || textMode == Format.TextMode.NORMALIZE || textMode == Format.TextMode.TRIM) {
      while (index < size) {
        if (!isAllWhitespace(content[index])) {
          return index
        }
        index++
      }
    }
    return index
  }

  // Return the index + 1 of the last non-all-whitespace CDATA or
  // Text node,  index < 0 is returned
  // if content contains all whitespace.
  // @param start index to begin search (exclusive)
  private fun skipTrailingWhite(content: List<Content>, start: Int): Int {
    var index = start
    val size = content.size
    if (index > size) {
      index = size
    }

    val textMode = format.textMode
    if (textMode == Format.TextMode.TRIM_FULL_WHITE || textMode == Format.TextMode.NORMALIZE || textMode == Format.TextMode.TRIM) {
      while (index >= 0) {
        if (!isAllWhitespace(content[index - 1])) {
          break
        }
        --index
      }
    }
    return index
  }

  private fun checkIsElementContainsSensitiveInformation(element: Element) {
    var name: String? = element.name

    if (!shouldCheckElement(element)) return

    if (doesNameSuggestSensitiveInformation(name!!) && !element.isEmpty) {
      logSensitiveInformationError(name, "Element", element.parentElement)
    }

    // checks only option tag
    name = element.getAttributeValue(Constants.NAME)
    if (name != null && doesNameSuggestSensitiveInformation(name) && element.getAttribute("value") != null) {
      logSensitiveInformationError("@name=$name", "Element", element /* here not parentElement because it is attributed */)
    }
  }

  private fun shouldCheckElement(element: Element): Boolean {
    //any user-provided name-value
    return !("property" == element.name && element.parentElement?.name.let { it == "driver-properties" || it == "driver"})
  }

  private fun logSensitiveInformationError(name: String, elementKind: String, parentElement: Element?) {
    val parentPath: String?
    if (parentElement == null) {
      parentPath = null
    }
    else {
      val ids = ArrayList<String>()
      var parent = parentElement
      while (parent != null) {
        var parentId = parent.name
        if (parentId == ComponentStorageUtil.COMPONENT) {
          val componentName = parent.getAttributeValue(ComponentStorageUtil.NAME)
          if (componentName != null) {
            parentId += "@$componentName"
          }
        }
        ids.add(parentId)
        parent = parent.parentElement
      }

      if (ids.isEmpty()) {
        parentPath = null
      }
      else {
        ids.reverse()
        parentPath = ids.joinToString(".")
      }
    }

    var message = "$elementKind ${if (parentPath == null) "" else "$parentPath."}$name probably contains sensitive information"
    if (storageFilePathForDebugPurposes != null) {
      message += " (file: ${storageFilePathForDebugPurposes.replace(FileUtilRt.toSystemIndependentName(SystemProperties.getUserHome()), "~")})"
    }
    if (reportedSensitiveProblems.add(message)) {
      Logger.getInstance(JbXmlOutputter::class.java).error(message)
    }
  }
}

// Return the next non-CDATA, non-Text, or non-EntityRef node,
// index = content.size() is returned if there is no more non-CDATA,
// non-Text, or non-EntryRef nodes
// @param start index to begin search (inclusive)
private fun nextNonText(content: List<Content>, start: Int): Int {
  var index = start
  if (index < 0) {
    index = 0
  }

  val size = content.size
  while (index < size) {
    val node = content[index]
    if (!(node is Text || node is EntityRef)) {
      return index
    }
    index++
  }
  return size
}

private fun printEntityRef(out: Writer, entity: EntityRef) {
  out.write("&")
  out.write(entity.name)
  out.write(";")
}

private fun isAllWhitespace(obj: Content): Boolean {
  val str = (obj as? Text ?: return false).text
  for (element in str) {
    if (!Verifier.isXMLWhitespace(element)) {
      return false
    }
  }
  return true
}

private fun startsWithWhite(str: String): Boolean {
  return !str.isEmpty() && Verifier.isXMLWhitespace(str[0])
}

// Determine if a string ends with an XML whitespace.
private fun endsWithWhite(str: String): Boolean {
  return !str.isEmpty() && Verifier.isXMLWhitespace(str[str.length - 1])
}

private fun escapeAttributeEntities(str: String): String {
  return JDOMUtil.escapeText(str, false, true)
}

private fun printQualifiedName(out: Writer, e: Element) {
  if (!e.namespace.prefix.isEmpty()) {
    out.write(e.namespace.prefix)
    out.write(':'.code)
  }
  out.write(e.name)
}

// Support method to print a name without using att.getQualifiedName()
// and thus avoiding a StringBuffer creation and memory churn
private fun printQualifiedName(out: Writer, a: Attribute) {
  val prefix = a.namespace.prefix
  if (!prefix.isNullOrEmpty()) {
    out.write(prefix)
    out.write(':'.code)
  }
  out.write(a.name)
}
