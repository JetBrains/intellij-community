// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework

import com.intellij.ide.actions.ViewStructureAction
import com.intellij.ide.structureView.newStructureView.StructurePopupTestExt
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider.Companion.getInstance
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.structureView.impl.StructureViewScopeHolder
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiFile
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.testFramework.utils.coroutines.waitCoroutinesBlocking
import com.intellij.ui.TreeSpeedSearch
import com.intellij.ui.treeStructure.Tree
import com.intellij.ui.treeStructure.filtered.FilteringTreeStructure.FilteringNode
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.launch

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

        if (popup is com.intellij.ide.util.FileStructurePopup) {
            PlatformTestUtil.waitForPromise(popup.select(popup.getCurrentElement()))
        } else if (popup is com.intellij.platform.structureView.frontend.FileStructurePopup) {
          val cs = StructureViewScopeHolder.getInstance(myFixture.project).cs.childScope("test scope")
          cs.launch {
            popup.selectCurrent()
          }
          waitCoroutinesBlocking(cs)
        }
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

                if (myPopup is com.intellij.platform.structureView.frontend.FileStructurePopup) {
                  val cs = StructureViewScopeHolder.getInstance(myFixture.project).cs.childScope("test scope")
                  cs.launch {
                    (myPopup as com.intellij.platform.structureView.frontend.FileStructurePopup).waitUpdateFinished()
                  }
                  waitCoroutinesBlocking(cs)
                }
            }
            return myPopup!!
        }

    override fun dispose() {
        myPopup = null
        myFile = null
    }

    companion object {
        fun update(popup: StructurePopupTestExt?, project: Project, doRebuild: Boolean = true) {
            if (doRebuild && popup is com.intellij.ide.util.FileStructurePopup) {
                PlatformTestUtil.waitForPromise(popup.rebuildAndUpdate())
            } else if (popup is com.intellij.platform.structureView.frontend.FileStructurePopup) {
              val cs = StructureViewScopeHolder.getInstance(project).cs.childScope("test scope")
              cs.launch {
                popup.waitUpdateFinished()
                if (doRebuild) popup.rebuildAndUpdate()
              }
              waitCoroutinesBlocking(cs)
            }
        }
    }
}