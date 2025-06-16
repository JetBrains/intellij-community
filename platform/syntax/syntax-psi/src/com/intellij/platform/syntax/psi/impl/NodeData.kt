// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.ASTFactory
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.platform.syntax.SyntaxElementType
import com.intellij.platform.syntax.lexer.TokenList
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.impl.source.tree.*
import com.intellij.psi.tree.*
import com.intellij.util.CharTable
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap
import kotlin.math.min

internal class NodeData(
  val lexStarts: IntArray,
  val offset: Int,
  val optionalData: CompositeOptionalData, // todo
  val text: CharSequence,
  val whitespaceTokens: TokenSet,
  val lexemeCount: Int,
  val convertedLexTypes: Array<IElementType>,
  val lexTypes: Array<SyntaxElementType>,
  var charTable: CharTable?,
  val astFactory: ASTFactory?, // parserDefinition as? ASTFactory
  val textArray: CharArray?,
  val file: PsiFile?,
) {
  val chameleonCache = Int2ObjectOpenHashMap<LazyParseableToken>()

  fun getLexemeType(index: Int): IElementType = convertedLexTypes[index]

  fun getLexemeStart(index: Int): Int = lexStarts[index]

  fun createLeaf(type: IElementType, start: Int, end: Int): TreeElement {
    val text = getInternedText(start, end)
    if (whitespaceTokens.contains(type)) {
      return PsiWhiteSpaceImpl(text)
    }

    if (type is ICustomParsingType) {
      return (type as ICustomParsingType).parse(text, charTable!!) as TreeElement
    }

    if (type is ILazyParseableElementType) {
      return createLazy(type, text, astFactory)
    }

    if (astFactory != null) {
      val element: TreeElement? = astFactory.createLeaf(type, text)
      if (element != null) return element
    }

    return ASTFactory.leaf(type, text)
  }

  fun createRootAST(rootMarker: CompositeNode): TreeElement {
    val type = rootMarker.tokenType
    val rootNode: TreeElement = if (type is ILazyParseableElementType) createLazy(type, null, astFactory)
    else createComposite(rootMarker, astFactory)
    if (charTable == null) {
      charTable = if (rootNode is FileElement) rootNode.charTable else CharTableImpl()
    }
    if (rootNode !is FileElement) {
      rootNode.putUserData(CharTable.CHAR_TABLE_KEY, charTable)
    }
    return rootNode
  }

  // todo get rid of astFactory parameter
  fun createComposite(marker: CompositeNode, astFactory: ASTFactory?): CompositeElement {
    val type = marker.tokenType
    if (type == TokenType.ERROR_ELEMENT) {
      val error = marker.errorMessage!!
      return Factory.createErrorElement(error)
    }

    if (type == null) {
      throw RuntimeException(UNBALANCED_MESSAGE)
    }

    if (astFactory != null) {
      val composite = astFactory.createComposite(marker.tokenType!!)
      if (composite != null) return composite
    }
    return ASTFactory.composite(type)
  }

  fun bind(rootMarker: CompositeNode, rootNode: CompositeElement) {
    val astFactory: ASTFactory? = this.astFactory
    var curMarker: CompositeNode? = rootMarker
    var curNode = rootNode

    var lexIndex = rootMarker.startIndex
    var item = if (rootMarker.myFirstChild != null) rootMarker.myFirstChild else rootMarker
    var itemDone = rootMarker.myFirstChild == null
    while (true) {
      lexIndex = insertLeaves(lexIndex, item!!.getLexemeIndex(itemDone), curNode)

      if (item === rootMarker && itemDone) break

      if (item is CompositeNode) {
        val marker = item
        if (itemDone) {
          curMarker = marker.parent as CompositeNode?
          curNode = curNode.treeParent
          item = marker.next
          itemDone = false
        }
        else if (!marker.isCollapsed) {
          curMarker = marker

          val childNode: CompositeElement = createComposite(marker, astFactory)
          curNode.rawAddChildrenWithoutNotifications(childNode)
          curNode = childNode

          item = if (marker.myFirstChild != null) marker.myFirstChild else marker
          itemDone = marker.myFirstChild == null
          continue
        }
        else {
          lexIndex = collapseLeaves(curNode, marker)
          item = marker.next
        }
      }
      else if (item is ErrorNode) {
        val errorElement = Factory.createErrorElement(item.getErrorMessage()!!)
        curNode.rawAddChildrenWithoutNotifications(errorElement)
        item = item.next
      }

      if (item == null) {
        item = curMarker
        itemDone = true
      }
    }
  }

  private fun insertLeaves(curToken: Int, lastIdx: Int, curNode: CompositeElement): Int {
    var curToken = curToken
    var lastIdx = lastIdx
    lastIdx = min(lastIdx, lexemeCount)
    while (curToken < lastIdx) {
      if ((curToken and 0xff) == 0) {
        ProgressIndicatorProvider.checkCanceled()
      }
      val start = getLexemeStart(curToken)
      val end = getLexemeStart(curToken + 1)
      if (start < end || getLexemeType(curToken) is ILeafElementType) {
        // Empty token. Most probably a parser directive like indent/dedent in Python
        val type = getLexemeType(curToken)
        val leaf = createLeaf(type, start, end)
        curNode.rawAddChildrenWithoutNotifications(leaf)
      }
      curToken++
    }

    return curToken
  }

  private fun collapseLeaves(ast: CompositeElement, compositeNode: CompositeNode): Int {
    val start = getLexemeStart(compositeNode.startIndex)
    val end = getLexemeStart(compositeNode.endIndex)
    val markerType = compositeNode.tokenType!!
    val leaf = createLeaf(markerType, start, end)
    if (shouldReuseCollapsedTokens(markerType) &&
        compositeNode.startIndex < compositeNode.endIndex
    ) {
      val length = compositeNode.endIndex - compositeNode.startIndex
      val relativeStarts = IntArray(length + 1)
      val types = arrayOfNulls<SyntaxElementType>(length + 1)
      for (i in compositeNode.startIndex..<compositeNode.endIndex) {
        relativeStarts[i - compositeNode.startIndex] = lexStarts[i] - start
        types[i - compositeNode.startIndex] = lexTypes[i]
      }
      relativeStarts[length] = end - start

      @Suppress("UNCHECKED_CAST")
      val tokenList = TokenList(
        lexStarts = relativeStarts,
        lexTypes = types as Array<SyntaxElementType>,
        tokenCount = length,
        tokenizedText = leaf.getChars())

      leaf.putUserData(LAZY_PARSEABLE_TOKENS, tokenList)
    }
    ast.rawAddChildrenWithoutNotifications(leaf)
    return compositeNode.endIndex
  }

  fun getInternedText(start: Int, end: Int): CharSequence =
    charTable!!.intern(text, start, end)

  private fun createLazy(
    type: ILazyParseableElementType,
    text: CharSequence?,
    astFactory: ASTFactory?,
  ): LazyParseableElement {
    return astFactory?.createLazy(type, text) ?: ASTFactory.lazy(type, text)
  }
}