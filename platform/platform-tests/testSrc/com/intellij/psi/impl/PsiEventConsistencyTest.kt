// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.psi.impl

import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileTypes.PlainTextLanguage
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiFileFactory
import com.intellij.psi.SyntaxTraverser
import com.intellij.psi.impl.source.CharTableImpl
import com.intellij.psi.impl.source.DummyHolder
import com.intellij.psi.impl.source.codeStyle.CodeEditUtil
import com.intellij.psi.impl.source.tree.*
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.testFramework.fixtures.LightPlatformCodeInsightFixtureTestCase
import org.jetbrains.jetCheck.Generator
import org.jetbrains.jetCheck.ImperativeCommand
import org.jetbrains.jetCheck.IntDistribution

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

  fun `test another case of changes in different AST parents at the same initial offset`() {
    val file = createEmptyFile()
    WriteCommandAction.runWriteCommandAction(project) {
      //prepare
      val root = file.node as FileElement
      root.replaceChild(root.firstChildNode, composite(compositeTypes[0],
                                                       composite(compositeTypes[0], leaf(leafTypes[0], "h")),
                                                       composite(compositeTypes[0], composite(compositeTypes[0]))))
      
      ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
        val emptyComposite = root.firstChildNode.lastChildNode.lastChildNode
        val hComposite = root.firstChildNode.firstChildNode
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          emptyComposite.addChild(leaf(leafTypes[0], "v"))
          hComposite.replaceChild(hComposite.firstChildNode, composite(compositeTypes[0]))
        }, root)
      }, root)
      assertEquals("v", root.text)
      assertEquals(root.text, file.viewProvider.document!!.text)
    }
  }
  
  fun `test changes on AST without PSI`() {
    val file = createEmptyFile()
    WriteCommandAction.runWriteCommandAction(project) {
      val root = file.node as FileElement
      root.replaceChild(root.firstChildNode, leaf(leafTypes[0], "A"))
      
      assertEquals("A", root.text)
      assertEquals(root.text, file.viewProvider.document!!.text)
    }
  }

  fun testPsiDocSynchronization() {
    ImperativeCommand.checkScenarios { RandomAstChanges() }
  }

  private inner class RandomAstChanges: ImperativeCommand {
    val file = PsiFileFactory.getInstance(project).createFileFromText("a.txt", PlainTextLanguage.INSTANCE, "", true,
                                                                      false)!!
    val document = file.viewProvider.document!!

    override fun performCommand(env: ImperativeCommand.Environment) {
      WriteCommandAction.runWriteCommandAction(project) {
        env.executeCommands(commands)
        assertEquals(document.text, file.text)
        assertEquals(document.text, file.node.text)
      }
    }

    private val commands: Generator<ImperativeCommand> = Generator.recursive { rec -> Generator.frequency(
      1,
      Generator.constant(ImperativeCommand { env ->
        env.logMessage("ChangeAction:")
        ChangeUtil.prepareAndRunChangeAction(ChangeUtil.ChangeAction {
          env.executeCommands(IntDistribution.uniform(1, 5), rec)
        }, file.node as FileElement)
      }),
      5,
      Generator.sampledFrom(DeleteElement(), ReplaceElement(), AddElement())
    ) }

    private fun randomNode(env: ImperativeCommand.Environment): TreeElement {
      val allNodes = SyntaxTraverser.astTraverser(file.node).toList()
      return env.generateValue(Generator.sampledFrom(allNodes), null) as TreeElement
    }

    private inner class DeleteElement : ImperativeCommand {
      override fun performCommand(env: ImperativeCommand.Environment) {
        val node = randomNode(env)
        val parent = node.treeParent ?: return
        env.logMessage("Deleting $node")
        parent.deleteChildInternal(node)
      }
    }
    private inner class ReplaceElement : ImperativeCommand {
      override fun performCommand(env: ImperativeCommand.Environment) {
        val node = randomNode(env)
        val parent = node.treeParent ?: return
        val replacement = env.generateValue(nodes, "Replacing $node with %s")
        parent.replaceChild(node, replacement)
      }
    }
    private inner class AddElement : ImperativeCommand {
      override fun performCommand(env: ImperativeCommand.Environment) {
        val composite = randomNode(env).let { it as? CompositeElement ?: it.treeParent }
        val children = composite.getChildren(null)
        val toAdd = env.generateValue(nodes, null)
        val anchorIndex = env.generateValue(Generator.integers(0, children.size), "Adding $toAdd into $composite at %s")
        composite.addChild(toAdd, if (anchorIndex == 0) null else children[anchorIndex - 1])
      }
    }
  }

  private fun createEmptyFile() : PsiFile =
    PsiFileFactory.getInstance(project).createFileFromText("a.txt", PlainTextLanguage.INSTANCE, "", true, false)

  private val leafTypes = (1..5).map { i -> IElementType("Leaf" + i, null) }
  private val compositeTypes = (1..5).map { i -> IElementType("Composite" + i, null) }

  private val leaves = Generator.zipWith(Generator.sampledFrom(leafTypes), Generator.asciiLetters()) { type, c ->
    leaf(type, c.toString())
  }

  private fun leaf(type: IElementType, text: String): TreeElement {
    if (text[0].isUpperCase()) {
      // no PSI
      return withDummyHolder(object : LeafElement(type, text) {
        override fun toString() = text
      })
    }

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


