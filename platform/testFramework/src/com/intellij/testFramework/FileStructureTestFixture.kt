// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.platform.structureView.impl.actions.ViewStructureAction
import com.intellij.platform.structureView.impl.StructurePopupTestExt
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure.FilteringNode
import com.intellij.util.ui.tree.TreeUtil

/**
 * @author Konstantin Bulenkov
 */
class FileStructureTestFixture(private val myFixture: CodeInsightTestFixture) : Disposable {
    private var myPopup: StructurePopupTestExt? = null
    private var myFile: PsiFile? = null

    fun updateAndSelectCurrent(): FilteringNode? {
        val popup = popup
        PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue() // Prevent write actions cancelling rebuild and update
        update()
        PlatformTestUtil.waitForPromise(popup.selectCurrentAsync())
        val path = popup.getTree().selectionPath
        return TreeUtil.getLastUserObject(FilteringNode::class.java, path)
    }

    fun update() {
        val popup = this.popup
        update(popup, myFixture.project)
    }

    val tree: Tree
        get() = this.popup.getTree()

    val speedSearch: TreeSpeedSearch?
        get() = this.popup.getSpeedSearch()

    val popup: StructurePopupTestExt
        get() {
            if (myPopup == null || myFile !== myFixture.getFile()) {
                if (myPopup != null) {
                    Disposer.dispose(myPopup!!)
                    myPopup = null
                }
                myFile = myFixture.getFile()
                myPopup = ViewStructureAction.createPopupForTest(
                    myFixture.getProject(),
                    getInstance().getTextEditor(myFixture.getEditor())
                )
                checkNotNull(myPopup)
                Disposer.register(this, myPopup!!)

                myPopup!!.initUi()
                PlatformTestUtil.waitForPromise(myPopup!!.waitUpdateFinishedAsync())
            }
            return myPopup!!
        }

    override fun dispose() {
        myPopup = null
        myFile = null
    }

  companion object {
    @JvmStatic
    fun waitUpdateFinished(popup: StructurePopupTestExt?, @Suppress("UNUSED_PARAMETER") project: Project) {
      if (popup != null) {
        PlatformTestUtil.waitForPromise(popup.waitUpdateFinishedAsync())
      }
    }

    @JvmStatic
    fun update(popup: StructurePopupTestExt?, @Suppress("UNUSED_PARAMETER") project: Project) {
      if (popup != null) {
        PlatformTestUtil.waitForPromise(popup.rebuildAndUpdateAsync())
      }
    }
  }
}