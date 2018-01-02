// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl

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
class PsiEventConsistencyTest : LightPlatformCodeInsightFixtureTestCase() {

  fun `test replacing child after changing its subtree`() {
    WriteCommandAction.runWriteCommandAction(project) {
      // prepare
      val root = createEmptyFile().node
      root.replaceChild(root.firstChildNode, composite(compositeTypes[0], leaf(leafTypes[0], "d")))
      
      // actual composite change
      ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
        root.firstChildNode.removeChild(root.firstChildNode.firstChildNode) // remove "d" leaf
        root.replaceChild(root.firstChildNode, composite(compositeTypes[0])) // replace now empty composite with another one 
      }, root as FileElement)
      assertEquals("", root.text)
    }
  }

  fun `test no excessive change merging between transactions if the second one has also a change higher in the tree`() {
    val file = createEmptyFile()
    WriteCommandAction.runWriteCommandAction(project) {
      //prepare
      val root = file.node as FileElement
      root.addChild(composite(compositeTypes[0], leaf(leafTypes[0], "a")), root.firstChildNode)
      
      ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
        // change in root child
        root.firstChildNode.replaceChild(root.firstChildNode.firstChildNode, leaf(leafTypes[0], "b"))
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          // change in root
          root.removeChild(root.lastChildNode)
          // change in the same root child as above
          root.firstChildNode.addChild(leaf(leafTypes[0], "c"), null)
        }, root)
      }, root)
      assertEquals("bc", root.text)
      assertEquals(root.text, file.viewProvider.document!!.text)
    }
  }

  fun `test replace then delete in different AST parents at the same final offset`() {
    val file = createEmptyFile()
    WriteCommandAction.runWriteCommandAction(project) {
      //prepare
      val root = file.node as FileElement
      root.replaceChild(root.firstChildNode, composite(compositeTypes[0],
                                                       composite(compositeTypes[0], leaf(leafTypes[0], "a")),
                                                       composite(compositeTypes[0], leaf(leafTypes[0], "b"))))
      
      ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
        val aComposite = root.firstChildNode.firstChildNode
        val bComposite = root.firstChildNode.lastChildNode
        bComposite.replaceChild(bComposite.firstChildNode, leaf(leafTypes[0], "B"))
        aComposite.removeChild(aComposite.firstChildNode)
      }, root)
      assertEquals("B", root.text)
      assertEquals(root.text, file.viewProvider.document!!.text)
    }
  }
  
  fun `test changes in different AST parents at the same initial offset`() {
    val file = createEmptyFile()
    WriteCommandAction.runWriteCommandAction(project) {
      //prepare
      val root = file.node as FileElement
      root.replaceChild(root.firstChildNode, composite(compositeTypes[0],
                                                       composite(compositeTypes[0]),
                                                       composite(compositeTypes[0], leaf(leafTypes[0], "x")),
                                                       composite(compositeTypes[0], leaf(leafTypes[0], "s"))))
      
      ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
        val pComposite = root.firstChildNode.firstChildNode
        val xComposite = pComposite.treeNext
        val sComposite = xComposite.treeNext
        sComposite.replaceChild(sComposite.firstChildNode, leaf(leafTypes[0], "h"))
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          pComposite.addChild(leaf(leafTypes[0], "p"))
          xComposite.removeChild(xComposite.firstChildNode)
        }, root)
      }, root)
      assertEquals("ph", root.text)
      assertEquals(root.text, file.viewProvider.document!!.text)
    }
  }

  fun testPsiDocSynchronization() {
    PropertyChecker.forAll(commands).shouldHold { cmd ->
      runCommand(cmd)
      true
    }
  }

  private fun runCommand(cmd: AstCommand) {
    val file = createEmptyFile()
    val document = file.viewProvider.document!!
    WriteCommandAction.runWriteCommandAction(project) {
      cmd.performChange(file)
      assertEquals(document.text, file.text)
      assertEquals(document.text, file.node.text)
    }
  }

  private fun createEmptyFile() : PsiFile = 
    PsiFileFactory.getInstance(project).createFileFromText("a.txt", PlainTextLanguage.INSTANCE, "", true, false)

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
    var found : String? = null
    fun findNode(file: PsiFile): TreeElement {
      val node = file.node as FileElement
      found = node.toString()
      var elem : TreeElement = node.findLeafElementAt(offsetHint % (file.textLength + 1)) ?: return node
      for (i in 0..depth) {
        elem = elem.treeParent ?: break
      }
      found = elem.toString()
      return elem
    }

    override fun toString() = found ?: "never"
  }

  private val genCoords = Generator.zipWith(Generator.naturals(), Generator.integers(0, 5), ::NodeCoordinates)
  private val commands: Generator<AstCommand> = Generator.recursive { rec -> Generator.frequency(
    1, Generator.listsOf(IntDistribution.uniform(1, 5), rec).map(::CommandGroup),
    5, genCoords.flatMap { coords ->
    Generator.anyOf(
      Generator.constant(DeleteElement(coords)),
      nodes.map { ReplaceElement(coords, it) },
      Generator.zipWith(nodes, Generator.naturals()) { n, i -> AddElement(coords, i, n) }
    ) }) }

  private val leafTypes = IntStreamEx.range(1, 5).mapToObj { i -> IElementType("Leaf" + i, null) }.toList()
  private val compositeTypes = IntStreamEx.range(1, 5).mapToObj { i -> IElementType("Composite" + i, null) }.toList()

  private val leaves = Generator.zipWith(Generator.sampledFrom(leafTypes), Generator.asciiLetters()) { type, c ->
    leaf(type, c.toString())
  }

  private fun leaf(type: IElementType, text: String): TreeElement {
    return withDummyHolder(object : LeafPsiElement(type, text) {
      override fun toString() = text
    })
  }

  private val composites : Generator<TreeElement> = Generator.sampledFrom(compositeTypes).flatMap { type ->
    Generator.listsOf(IntDistribution.uniform(0, 5), nodes).map { children ->
      composite(type, *children.toTypedArray())
    }
  }

  private fun composite(type: IElementType, vararg children: TreeElement): TreeElement {
    val composite = withDummyHolder(object : CompositePsiElement(type) {
      override fun toString() = getChildren(null).asList().toString()
    })
    children.forEach(composite::addChild)
    return composite
  }

  private val nodes = Generator.frequency(4, leaves, 2, composites)

  private fun withDummyHolder(e: TreeElement): TreeElement {
    DummyHolder(LightPlatformTestCase.getPsiManager(), e, null, CharTableImpl())
    CodeEditUtil.setNodeGenerated(e, true)
    return e
  }

}


