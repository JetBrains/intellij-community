// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl

import com.intellij.lang.ASTFactory
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.*
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import jetCheck.Generator
import jetCheck.IntDistribution
import jetCheck.PropertyChecker
import one.util.streamex.IntStreamEx

/**
 * @author peter
 */
@Suppress("unused") // enable after fixing the bugs due to which the test fails
abstract class PsiEventConsistencyTest : LightPlatformCodeInsightFixtureTestCase() {

  fun testPsiDocSynchronization() {
    PropertyChecker.forAll(commands()).shouldHold { cmd ->
      val file = PsiFileFactory.getInstance(project).createFileFromText("a.txt", PlainTextLanguage.INSTANCE, "", true, false)
      val document = file.viewProvider.document!!
      WriteCommandAction.runWriteCommandAction(project) {
        cmd.performChange(file)
        assertEquals(document.text, file.text)
        assertEquals(document.text, file.node.text)
      }
      true
    }
  }

  private interface AstCommand {
    fun performChange(file: PsiFile)
  }
  
  private data class CommandGroup(val inside: List<AstCommand>): AstCommand {
    override fun performChange(file: PsiFile) {
      ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction { 
        inside.forEach { it.performChange(file) }
      }, file.node as FileElement)
    }
  }

  private data class DeleteElement(val coord: NodeCoordinates): AstCommand {
    override fun performChange(file: PsiFile) {
      val node = coord.findNode(file)
      node.treeParent?.deleteChildInternal(node)
    }
  }

  private data class ReplaceElement(val coord: NodeCoordinates, val replacement: TreeElement): AstCommand {
    override fun performChange(file: PsiFile) {
      val node = coord.findNode(file)
      node.treeParent?.replaceChild(node, replacement)
    }
  }

  private data class AddElement(val coord: NodeCoordinates, val indexHint: Int, val toAdd : TreeElement): AstCommand {
    override fun performChange(file: PsiFile) {
      val composite = coord.findNode(file).let { it as? CompositeElement ?: it.treeParent }
      val children = composite.getChildren(null)
      val anchorIndex = indexHint % (children.size + 1)
      composite.addChild(toAdd, if (anchorIndex == 0) null else children[anchorIndex - 1])
    }
  }

  private data class NodeCoordinates(val offsetHint: Int, val depth: Int) {
    fun findNode(file: PsiFile): TreeElement {
      val node = file.node as FileElement
      var elem : TreeElement = node.findLeafElementAt(offsetHint % (file.textLength + 1)) ?: return node
      for (i in 0..depth) {
        elem = elem.treeParent ?: break
      }
      return elem
    }
  }

  private val genCoords = Generator.zipWith(Generator.naturals(), Generator.integers(0, 5), ::NodeCoordinates)
  private fun commands(): Generator<AstCommand> = Generator.frequency(
    1, Generator.from { CommandGroup(Generator.listsOf(IntDistribution.uniform(1, 5), commands()).generateValue(it)) },
    5, genCoords.flatMap { coords ->
    Generator.anyOf(
      Generator.constant(DeleteElement(coords)),
      nodes.map { ReplaceElement(coords, it) },
      Generator.zipWith(nodes, Generator.naturals()) { n, i -> AddElement(coords, i, n) }
    ) })

  private val leafTypes = IntStreamEx.range(1, 5).mapToObj { i -> IElementType("Leaf" + i, null) }.toList()
  private val compositeTypes = IntStreamEx.range(1, 5).mapToObj { i -> IElementType("Composite" + i, null) }.toList()

  private val leaves = Generator.zipWith(Generator.sampledFrom(leafTypes), Generator.asciiLetters()) { type, c ->
    withDummyHolder(ASTFactory.leaf(type, c.toString())) }

  private val composites : Generator<TreeElement> = Generator.sampledFrom(compositeTypes).flatMap { type ->
    Generator.listsOf(IntDistribution.uniform(0, 5), nodes).map { children ->
      val composite = withDummyHolder(object: CompositePsiElement(type) {
        override fun toString(): String {
          return super.toString() + children.toString()
        }
      })
      children.forEach(composite::addChild)
      composite
    }
  }
  private val nodes = Generator.frequency(4, leaves, 2, composites)

  private fun withDummyHolder(e: TreeElement): TreeElement {
    DummyHolder(LightPlatformTestCase.getPsiManager(), e, null, CharTableImpl())
    CodeEditUtil.setNodeGenerated(e, true)
    return e
  }

}


