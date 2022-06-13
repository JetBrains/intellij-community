// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.codegen.patcher

import com.intellij.workspaceModel.codegen.deft.model.*
import java.util.*

// todo: replace by real kotlin parser
class KotlinReader(val file: KtFile) {
  val text: CharSequence = file.content()
  var pos = 0
  val c get() = text[pos]
  val end get() = pos >= text.length

  var leafScope: KtScope = file.scope
  var leafBlock: KtBlock = file.block
  var leafConstructor: KtConstructor? = null

  fun read() {
    head()
    blockContents()
  }

  fun head() {
    var pkg: String? = null
    var importsStart: Int = 0
    var importsEnd: Int = 0
    val imports = mutableSetOf<String>()
    while (!end) {
      when {
        isNext("package ") -> {
          skipSpaces()
          pkg = toLineEnd()
          importsEnd = pos
          if (pkg == null) break
        }
        isNext("import ") -> {
          if (importsStart == 0) importsStart = pos - "import ".length
          skipSpaces()
          val imprt = toLineEnd()
          importsEnd = pos
          if (imprt == null) break
          imports.add(imprt.trim())
        }
        c.isWhitespace() -> skipSpaces()
        c == '/' && preview() == '/' -> lineComment()
        c == '/' && preview() == '*' -> blockComment()
        else -> break
      }
    }

    file.setPackage(pkg)
    file.setImports(KtImports(importsStart..importsEnd, imports))
  }

  fun next(string: String): Boolean {
    val indexOf = text.indexOf(string, pos)
    if (indexOf == -1) return false
    pos = indexOf + string.length
    return true
  }

  fun skipSpaces() {
    while (!end && c.isWhitespace()) pos++
  }

  class Iden(
    val name: SrcRange,
    val receiver: SrcRange? = null
  )

  fun iden(): Iden? {
    val start = pos
    var nameStart = pos
    var receiver: SrcRange? = null
    if (!c.isJavaIdentifierStart()) return null
    pos++
    while (!end) {
      when {
        c == '.' -> {
          receiver = SrcRange(src, start until pos)
          pos++
          skipSpaces()
          nameStart = pos
        }
        c.isJavaIdentifierPart() -> pos++
        else -> break
      }
    }
    return Iden(SrcRange(src, nameStart until pos), receiver)
  }

  fun maybeType(): KtType? {
    if (end || c != ':') return null
    pos++
    skipSpaces()
    return type()
  }

  fun maybeTypes(): List<KtType> {
    if (end || c != ':') return listOf()
    pos++
    val result = mutableListOf<KtType>()
    while (true) {
      skipSpaces()
      result.add(type() ?: break)
      skipParentheses()
      val backup = pos
      skipSpaces()
      if (end || c != ',') {
        pos = backup
        break
      }
      pos++
    }
    return result
  }

  private val src = file.asSrc()

  fun type(classifer: SrcRange? = null, inArgs: Boolean = false): KtType? {
    val args = mutableListOf<KtType>()
    var lastPos = pos
    val annotations = mutableListOf<KtAnnotation>()
    fun withLastAnnotations(target: KtType): KtType {
      target.annotations.list.addAll(annotations)
      annotations.clear()
      return target
    }

    fun last(): SrcRange = src.range(lastPos until pos)
    fun lastType(): KtType {
      return withLastAnnotations(KtType(last()))
    }

    while (!end) {
      when {
        c.isJavaIdentifierPart() || c == '.' -> pos++
        c == '@' -> {
          pos++
          annotation()?.let { annotations.add(it) }
          skipSpaces()
          lastPos = pos
        }
        c == '<' -> {
          val classifier = last()
          pos++
          skipSpaces()
          val result = withLastAnnotations(type(classifier, inArgs = true) ?: return null)
          args.add(result)
          pos++
          return result
        }
        inArgs && c.isWhitespace() -> pos++
        inArgs && c == '*' -> {
          pos++
          skipSpaces()
        }
        inArgs && c == ',' -> {
          args.add(lastType())
          pos++
          lastPos = pos
          skipSpaces()
        }
        inArgs && c == '>' -> {
          args.add(lastType())
          if (preview() == '?') {
            pos++
            return KtType(classifer!!, args, optional = true)
          } else {
            return KtType(classifer!!, args)
          }
        }
        else -> {
          var optional = false
          val range = last()
          if (c == '?') {
            optional = true
            pos++
          }
          return if (classifer != null) KtType(classifer, args, optional, annotations)
          else KtType(range, optional = optional, annotations = annotations)
        }
      }
    }
    return null
  }

  fun preview(): Char? = if (end) null else text[pos + 1]
  fun isNext(str: String): Boolean {
    if (pos + str.length < text.length) {
      if (text.substring(pos, pos + str.length) == str) {
        pos += str.length
        return true
      }
    }

    return false
  }

  fun maybeBlock(prevElementEnd: Int = pos, iface: KtScope? = null): KtBlock {
    val outer = leafBlock
    if (end || c != '{') {
      // Class has an empty body and the source skips curly braces
      val inner = KtBlock(src, outer, isStub = true, scope = iface)
      outer.children.add(inner)
      return inner
    }
    pos++

    val inner = KtBlock(src, outer, scope = iface)
    outer.children.add(inner)
    leafBlock = inner
    inner.range = range { blockContents() }
    leafBlock = outer
    return inner
  }

  private fun maybePrimaryConstructor(iface: KtScope? = null): KtConstructor? {
    if (end || c != '(') return null
    pos++

    val outer = leafConstructor
    val constructor = KtConstructor(scope = iface)
    leafConstructor = constructor
    constructor.range = range { primaryConstructor() }
    leafConstructor = outer
    return constructor
  }

  private fun primaryConstructor(): CharSequence? {
    val start = pos
    skipSpaces()
    var annotations = KtAnnotations()
    fun annotations(): KtAnnotations {
      return annotations.also { annotations = KtAnnotations() }
    }
    while (!end) {
      when {
        c == ')' -> {
          val end = pos
          pos++
          return text.subSequence(start, end)
        }
        c == '\"' -> skipStringConst()
        c == '(' -> maybePrimaryConstructor()
        c == '/' && preview() == '/' -> lineComment()
        c == '/' && preview() == '*' -> blockComment()
        c == '@' -> {
          pos++
          annotation()?.let { annotations.list.add(it) }
        }
        isNext("var ") || isNext("val ") -> constructorVariable(annotations())
        else -> pos++
      }
    }
    return null
  }

  fun skipPrimaryConstructor() {
    skipParentheses()
  }

  private fun skipParentheses() {
    if (end || c != '(') {
      return
    }
    while (c != ')') {
      pos++
    }
    pos++
  }

  fun blockContents(): CharSequence? {
    val start = pos
    skipSpaces()
    var annotations = KtAnnotations()
    fun annotations(): KtAnnotations {
      return annotations.also { annotations = KtAnnotations() }
    }
    while (!end) {
      when {
        c == '}' -> {
          val end = pos
          pos++
          return text.subSequence(start, end)
        }
        c == '\"' -> skipStringConst()
        c == '{' -> maybeBlock()
        c == '/' && preview() == '/' -> lineComment()
        c == '/' && preview() == '*' -> blockComment()
        c == '@' -> {
          pos++
          annotation()?.let { annotations.list.add(it) }
        }
        isNext("data ") && isNext("class ") -> `interface`(annotations(), WsData)
        isNext("sealed ") && isNext("class ") -> `interface`(annotations(), WsSealed)
        isNext("enum ") && isNext("class ") -> `interface`(annotations(), WsEnum)
        isNext("object ") -> `interface`(annotations(), WsObject)
        isNext("interface ") -> `interface`(annotations())
        isNext("override ") && isNext("val ") -> `val`(annotations())
        isNext("val ") -> `val`(annotations())
        isNext("suspend ") && isNext("fun ") -> `fun`(annotations())
        else -> pos++
      }
    }
    return null
  }

  private fun annotation(): KtAnnotation? {
    val nameStart = pos
    var nameEnd = nameStart
    var args: List<SrcRange> = listOf()
    if (!c.isJavaIdentifierStart()) return null
    pos++
    while (!end) {
      when {
        c.isJavaIdentifierPart() -> {
          pos++
          nameEnd = pos
        }
        c == '(' -> {
          nameEnd = pos
          args = annotationArgs() ?: return null
        }
        else -> break
      }
    }

    return KtAnnotation(src.range(nameStart until nameEnd), args)
  }

  private fun annotationArgs(): List<SrcRange>? {
    pos++
    skipSpaces()
    val result = mutableListOf<SrcRange>()
    var start = pos
    while (!end) {
      when (c) {
        '(' -> annotationArgs()
        ',' -> {
          result.add(src.range(start until pos))
          pos++
          skipSpaces()
          start = pos
        }
        ')' -> {
          result.add(src.range(start until pos))
          pos++
          return result
        }
        else -> pos++
      }
    }
    return null
  }

  fun `interface`(annotations: KtAnnotations, predefinedInterfaceKind: KtInterfaceKind? = null) {
    skipSpaces()
    val name = iden()?.name ?: return
    skipSpaces()
    val constructor = maybePrimaryConstructor()
    skipSpaces()
    val superTypes = maybeTypes()

    val outer = leafScope
    val innerIface = KtInterface(file.module, outer, name, superTypes, constructor, predefinedInterfaceKind, annotations)
    val inner = innerIface.scope
    outer.def(name.text, inner)
    leafScope = inner
    val start = pos
    skipSpaces()
    innerIface.body = maybeBlock(start, inner)
    leafScope = outer
  }

  private inline fun maybeRange(read: () -> Boolean): SrcRange? {
    val start = pos
    if (!read()) return null
    val end = pos - 2
    return src.range(start..end)
  }

  private inline fun range(read: () -> Unit): SrcRange = maybeRange { read(); true }!!

  private fun constructorVariable(annotations: KtAnnotations) {
    skipSpaces()
    val iden = iden() ?: return
    skipSpaces()
    val type = maybeType()
    skipSpaces()
    val getterBody = maybeGetter()
    val extensionDelegateModuleName = if (getterBody != null) null else maybeExtensionDelegateModuleName()
    leafConstructor?.defs?.add(DefField(
      iden.name,
      iden.name.text,
      type,
      getterBody != null,
      getterBody,
      true,
      suspend = false,
      annotations,
      iden.receiver?.let { KtType(it) },
      extensionDelegateModuleName
    ))
  }

  private fun `val`(annotations: KtAnnotations) {
    skipSpaces()
    val iden = iden() ?: return
    skipSpaces()
    val type = maybeType()
    skipSpaces()
    val getterBody = maybeGetter()
    val extensionDelegateModuleName = if (getterBody != null) null else maybeExtensionDelegateModuleName()
    leafBlock.defs.add(DefField(
      iden.name,
      iden.name.text,
      type,
      getterBody != null,
      getterBody,
      false,
      suspend = false,
      annotations,
      iden.receiver?.let { KtType(it) },
      extensionDelegateModuleName
    ))
  }

  private fun maybeExtensionDelegateModuleName(): SrcRange? {
    if (!isNext("by")) return null
    skipSpaces()
    val delegateIden = iden() ?: return null
    if (delegateIden.receiver == null) return null
    if (delegateIden.name.text != "extensions") return null
    return delegateIden.receiver
  }

  private fun `fun`(annotations: KtAnnotations) {
    skipSpaces()
    val name = iden()?.name ?: return
    skipSpaces()
    if (!isNext("()")) return
    skipSpaces()
    val type = maybeType()
    skipSpaces()
    val hasExpr = c == '=' || c == '{'
    if (name.text.startsWith("get")) {
      val realName = name.text.removePrefix("get").replaceFirstChar { it.lowercase(Locale.getDefault()) }
      leafBlock.defs.add(DefField(name, realName, type, hasExpr, null, false, suspend = true, annotations))
    }
  }

  fun maybeGetter(): String? {
    if (!isNext("get()")) return null
    skipSpaces()
    return when (c) {
      '{' -> maybeBlock().text
      '=' -> untilNewLine()
      else -> null
    }
  }

  private fun skipStringConst(): Boolean {
    pos++
    while (!end) {
      when (c) {
        '\"' -> {
          pos++
          return true
        }
        '\\' -> {
          pos++
          pos++
        }
        '$' -> {
          pos++
          if (end) break
          when (c) {
            '{' -> {
              pos++
              while (!end) {
                when (c) {
                  '}' -> break
                  '{' -> maybeBlock()
                }
                pos++
              }
            }
            else -> while (!end && c.isJavaIdentifierPart()) pos++
          }
        }
        else -> pos++
      }
    }
    return false
  }

  private fun lineComment() {
    val commentStart = pos
    pos++
    pos++
    val comment = untilNewLine()
    var b = leafBlock
    when (comment) {
      "region generated code" -> {
        var lineStart = commentStart
        while (lineStart > 1 && text[lineStart - 1].isWhitespace()) {
          if (text[lineStart - 1] == '\n') break
          lineStart--
        }
        // Handle code block with extensions
        if (leafBlock.parent == null) {
          b = leafBlock.children.last()
          b._extensionCode = lineStart..Int.MAX_VALUE
          untilEndRegion(b)
        } else if (b._generatedCode == null) {
          b._generatedCode = commentStart..Int.MAX_VALUE
        }
      }
      "endregion" -> {
        if (b._generatedCode != null) {
          b._generatedCode = b._generatedCode!!.first..pos
        }
      }
      else -> Unit
    }
  }

  private fun untilEndRegion(block: KtBlock) {
    while (!end) {
      when {
        c == '/' && preview() == '/' ->  {
          pos++
          pos++
          if (untilNewLine() == "endregion") {
            block._extensionCode = block._extensionCode!!.first..pos
            return
          } else {
            pos++
          }
        }
        else -> pos++
      }
    }
  }

  private fun untilNewLine(): String {
    val start = pos
    while (!end) {
      when {
        c == '\n' -> break
        else -> pos++
      }
    }
    return text.substring(start, pos)
  }

  private fun blockComment() {
    pos++
    pos++
    while (!end) {
      when {
        c == '*' && preview() == '/' -> {
          pos++
          pos++
          return
        }
        c == '/' && preview() == '*' -> blockComment()
        else -> pos++
      }
    }
  }

  override fun toString(): String = SrcPos(src, pos).toString()

  private fun toLineEnd(): String? {
    val start = pos
    while (!end) {
      when {
        c == '\n' -> {
          val str = text.substring(start, pos)
          pos++
          return str
        }
        else -> pos++
      }
    }
    return null
  }
}