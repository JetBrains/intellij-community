// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.psi.impl

import com.intellij.lang.LighterASTNode
import com.intellij.openapi.util.Ref
import com.intellij.psi.tree.IElementType
import com.intellij.psi.tree.ILeafElementType
import com.intellij.psi.tree.ILightLazyParseableElementType
import com.intellij.util.containers.LimitedPool
import com.intellij.util.diff.FlyweightCapableTreeStructure
import kotlin.math.min

internal class MyTreeStructure(
  private val root: CompositeNode,
  parentTree: MyTreeStructure?
) : FlyweightCapableTreeStructure<LighterASTNode> {

  private val rangePool: LimitedPool<TokenRangeNode> = parentTree?.rangePool ?: LimitedPool<TokenRangeNode>(1000, object : LimitedPool.ObjectFactory<TokenRangeNode> {
    override fun create(): TokenRangeNode = TokenRangeNode()
  })

  private val lexemePool: LimitedPool<SingleLexemeNode> = parentTree?.lexemePool ?: LimitedPool<SingleLexemeNode>(1000, object : LimitedPool.ObjectFactory<SingleLexemeNode> {
    override fun create(): SingleLexemeNode = SingleLexemeNode()
  })

  // used by getChildren
  private var count = 0
  private var nodes: Array<LighterASTNode?>? = null

  override fun getRoot(): LighterASTNode = root

  override fun getParent(node: LighterASTNode): LighterASTNode? {
    if (node is NodeBase) {
      return node.parent
    }
    if (node is Token) {
      return node.parentNode
    }
    throw UnsupportedOperationException("Unknown node type: $node")
  }

  override fun getChildren(item: LighterASTNode, into: Ref<Array<LighterASTNode>>): Int {
    if (item is LazyParseableToken) {
      val tree = item.parseContents()
      val root = tree.getRoot()
      if (root is NodeBase) {
        root.parent = item.parentNode
      }
      return tree.getChildren(root, into) // todo: set offset shift for kids?
    }

    if (item is Token || item is ErrorNode) return 0
    val marker = item as CompositeNode

    count = 0
    var child = marker.myFirstChild
    var lexIndex = marker.startIndex
    while (child != null) {
      lexIndex = insertLeaves(lexIndex, child.startIndex, marker.nodeData, marker)

      if (child is CompositeNode && child.isCollapsed) {
        val lastIndex = child.endIndex
        insertLeaf(child.tokenType!!, marker.nodeData, child.startIndex, lastIndex, true, marker)
      }
      else {
        val nodes = ensureNodesCapacity()
        nodes[count++] = child
      }

      if (child is CompositeNode) {
        lexIndex = child.endIndex
      }
      child = child.next
    }

    insertLeaves(lexIndex, marker.endIndex, marker.nodeData, marker)
    into.set(if (nodes == null) LighterASTNode.EMPTY_ARRAY else nodes)
    nodes = null

    return count
  }

  override fun disposeChildren(nodes: Array<LighterASTNode?>?, count: Int) {
    if (nodes == null) return
    for (i in 0..<count) {
      val node = nodes[i]
      if (node is TokenRangeNode) {
        rangePool.recycle(node)
      }
      else if (node is SingleLexemeNode) {
        lexemePool.recycle(node)
      }
    }
  }

  private fun ensureNodesCapacity(): Array<LighterASTNode?> {
    val old = nodes
    if (old == null) {
      val new = arrayOfNulls<LighterASTNode?>(10)
      nodes = new
      return new
    }
    else if (count >= old.size) {
      val new = old.copyOf(count * 3 / 2)
      nodes = new
      return new
    }
    else {
      return old
    }
  }

  fun insertLeaves(curToken: Int, lastIdx: Int, data: NodeData, parent: CompositeNode): Int {
    var curToken = curToken
    var lastIdx = lastIdx
    lastIdx = min(lastIdx, data.lexemeCount)
    while (curToken < lastIdx) {
      insertLeaf(data.convertedLexTypes[curToken], data, curToken, curToken + 1, false, parent)

      curToken++
    }
    return curToken
  }

  fun insertLeaf(
    type: IElementType,
    data: NodeData,
    startLexemeIndex: Int,
    endLexemeIndex: Int,
    forceInsertion: Boolean,
    parent: CompositeNode,
  ) {
    val start = data.lexStarts[startLexemeIndex]
    val end = data.lexStarts[endLexemeIndex]

    /* Corresponding code for heavy tree is located in `PsiBuilderImpl#insertLeaves` and is applied only to plain lexemes */
    if (start > end || !forceInsertion && start == end && type !is ILeafElementType) {
      return
    }

    val lexeme: Token
    if (type is ILightLazyParseableElementType) {
      val startInFile = start + data.offset
      var token = data.chameleonCache.get(startInFile)
      if (token == null) {
        token = LazyParseableToken(this, startLexemeIndex, endLexemeIndex)
        token.initToken(type, parent, start, end)
        data.chameleonCache.put(startInFile, token)
      }
      else if (token.nodeData !== data || token.startIndex != startLexemeIndex || token.endIndex != endLexemeIndex) {
        throw AssertionError("Wrong chameleon cached")
      }
      lexeme = token
    }
    else if (startLexemeIndex == endLexemeIndex - 1 && type === data.convertedLexTypes[startLexemeIndex]) {
      val single = lexemePool.alloc()
      single.parentNode = parent
      single.lexemeIndex = startLexemeIndex
      lexeme = single
    }
    else {
      val collapsed = rangePool.alloc()
      collapsed.initToken(type, parent, start, end)
      lexeme = collapsed
    }
    val nodes = ensureNodesCapacity()
    nodes[count++] = lexeme
  }

  override fun toString(node: LighterASTNode): CharSequence {
    return root.nodeData.text.subSequence(node.getStartOffset(), node.getEndOffset())
  }

  override fun getStartOffset(node: LighterASTNode): Int {
    return node.getStartOffset()
  }

  override fun getEndOffset(node: LighterASTNode): Int {
    return node.getEndOffset()
  }
}