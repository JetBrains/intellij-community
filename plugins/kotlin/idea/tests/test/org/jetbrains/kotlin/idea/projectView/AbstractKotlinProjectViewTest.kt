// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.projectView

import com.intellij.ide.projectView.impl.ProjectViewFileNestingService
import com.intellij.ide.projectView.impl.ProjectViewFileNestingService.NestingRule
import com.intellij.ide.projectView.impl.nodes.AbstractPsiBasedNode
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.JarFileSystem
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.psi.PsiElement
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.assertEqualsToFile
import com.intellij.util.CommonProcessors
import com.intellij.util.ui.tree.TreeUtil
import org.jetbrains.kotlin.idea.core.util.toPsiFile
import org.jetbrains.kotlin.idea.test.Directives
import org.jetbrains.kotlin.idea.test.KotlinMultiFileHeavyProjectTestCase
import java.io.File
import javax.swing.tree.DefaultMutableTreeNode
import kotlin.io.path.*

abstract class AbstractKotlinProjectViewTest : KotlinMultiFileHeavyProjectTestCase() {
    private lateinit var treeStructure: TestProjectTreeStructure

    override fun setUp() {
        super.setUp()
        treeStructure = TestProjectTreeStructure(project, testRootDisposable)

        val initialNestingRules = ProjectViewFileNestingService.getInstance().rules
        Disposer.register(testRootDisposable) { ProjectViewFileNestingService.getInstance().rules = initialNestingRules }
    }

    override fun doMultiFileTest(testDataPath: String, globalDirectives: Directives) {
        val path = Path(globalDirectives.getValue("PATH"))
        val processor = object : CommonProcessors.FindFirstProcessor<VirtualFile>() {
            override fun accept(t: VirtualFile): Boolean = Path(t.path).endsWith(path)
        }
        treeStructure.isShowMembers = globalDirectives.getBooleanValue("SHOW_MEMBERS")

        globalDirectives.listValues("NESTING_RULE")?.forEach { rule ->
            rule.split("->").let {
                val parentSuffix = it[0]
                val childSuffix = it[1]
                val nestingService = ProjectViewFileNestingService.getInstance()
                nestingService.setRules(nestingService.rules + NestingRule(parentSuffix, childSuffix))
            }
        }

        FilenameIndex.processFilesByName(path.name, true, GlobalSearchScope.allScope(project), processor)
        val resultFile = processor.foundValue ?: error("$path file is not found")

        val pane = treeStructure.createPane()
        val psiFile = resultFile.toPsiFile(project)
        pane.select(psiFile, resultFile, true)

        val tree = pane.tree
        PlatformTestUtil.waitWhileBusy(tree)

        globalDirectives.listValues("EXPAND_ROW")?.forEach {
            PlatformTestUtil.expand(tree, it.toInt())
        }

        val node = TreeUtil.findNode(tree.model.root as DefaultMutableTreeNode) {
            val userObject = it.userObject
            userObject is PsiElement && userObject.containingFile?.virtualFile == resultFile ||
                    userObject is AbstractPsiBasedNode<*> && (userObject.value as? PsiElement)?.containingFile?.virtualFile == resultFile
        } ?: error("node is not found")

        PlatformTestUtil.waitForCallback(TreeUtil.selectInTree(node, true, tree))

        val psiBasedNode = node.userObject as? AbstractPsiBasedNode<*>
        val navigationItem = psiBasedNode?.navigationItem
        val actualTree = PlatformTestUtil.print(/* tree = */ tree, /* withSelection = */ true)

        assertEqualsToFile(
            description = "The tree is different",
            expected = File(testDataPath.substringBeforeLast('.') + ".txt"),
            actual = sanitizeTree(
                "Node: $node\n" +
                        "User object class: ${psiBasedNode?.let { it::class.simpleName }}\n\n" +
                        "Value: ${psiBasedNode?.value}\n" +
                        "Value file: ${filePath(psiBasedNode?.value)}\n\n" +
                        deepNavigation(navigationItem) + "\n\n" +
                        actualTree
            )
        )
    }

    private fun filePath(element: Any?): String? {
        if (element !is PsiElement) return null

        val virtualFile = element.containingFile.virtualFile
        val fileSystem = virtualFile.fileSystem
        val prefixFile = if (fileSystem is JarFileSystem) {
            fileSystem.getVirtualFileForJar(virtualFile)?.path?.let(::Path)?.parent!!
        } else {
            module.moduleNioFile.parent.listDirectoryEntries().single()
        }

        val path = Path(virtualFile.path)
        return path.relativeTo(prefixFile).pathString
    }

    private fun deepNavigation(element: Any?): String {
        if (element == null) return "Navigation item not found"
        return buildString {
            deepNavigation(element, 0)
        }
    }

    private fun StringBuilder.deepNavigation(element: Any, count: Int) {
        appendLine("Navigation item #${count}: $element")
        appendLine("Navigation item file #${count}: ${filePath(element)}")

        if (element !is PsiElement) return
        val navigateElement = element.navigationElement
        if (navigateElement == element) return
        deepNavigation(navigateElement, count + 1)
    }

    private fun sanitizeTree(tree: String): String {
        val resultSequence = Holder.STDLIB_REGEX.findAll(tree)
        var resultString = tree
        for (matchResult in resultSequence) {
            resultString = resultString.replace(matchResult.value, matchResult.groupValues[1] + matchResult.groupValues[3])
        }

        return resultString
    }

    private object Holder {
        val STDLIB_REGEX: Regex = Regex("(kotlin-stdlib.*?)(-\\d.*)([.]jar)")
    }
}
