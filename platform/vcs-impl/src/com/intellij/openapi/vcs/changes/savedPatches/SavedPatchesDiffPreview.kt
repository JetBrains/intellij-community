// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.savedPatches

import com.intellij.diff.FrameDiffTool
import com.intellij.diff.chains.DiffRequestProducer
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeViewDiffRequestProcessor
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesUi.Companion.SAVED_PATCHES_UI_PLACE
import com.intellij.openapi.vcs.changes.ui.*
import org.jetbrains.annotations.Nls
import java.beans.PropertyChangeListener
import javax.swing.JTree

class SavedPatchesDiffPreview(project: Project,
                              private val tree: ChangesTree,
                              private val isInEditor: Boolean,
                              parentDisposable: Disposable)
  : ChangeViewDiffRequestProcessor(project, SAVED_PATCHES_UI_PLACE) {
  private val disposableFlag = Disposer.newCheckedDisposable()

  init {
    tree.addSelectionListener(Runnable {
      updatePreviewLater(false)
    }, this)
    tree.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY, PropertyChangeListener {
      updatePreviewLater(false)
    })

    Disposer.register(parentDisposable, this)
    Disposer.register(this, disposableFlag)

    updatePreviewLater(false)
  }

  override fun shouldAddToolbarBottomBorder(toolbarComponents: FrameDiffTool.ToolbarComponents): Boolean {
    return !isInEditor || super.shouldAddToolbarBottomBorder(toolbarComponents)
  }

  private fun updatePreviewLater(modelUpdateInProgress: Boolean) {
    ApplicationManager.getApplication().invokeLater({ updatePreview(component.isShowing, modelUpdateInProgress) }) {
      disposableFlag.isDisposed
    }
  }

  override fun iterateSelectedChanges(): Iterable<Wrapper> {
    return wrap(VcsTreeModelData.selected(tree))
  }

  override fun iterateAllChanges(): Iterable<Wrapper> {
    return wrap(VcsTreeModelData.all(tree))
  }

  override fun selectChange(change: Wrapper) {
    ChangesBrowserBase.selectObjectWithTag(tree, change.userObject, change.tag)
  }

  private fun wrap(treeModelData: VcsTreeModelData): Iterable<Wrapper> {
    return treeModelData.iterateUserObjects(SavedPatchesProvider.ChangeObject::class.java)
      .map { MyChangeWrapper(it) }
  }

  private inner class MyChangeWrapper(private val change: SavedPatchesProvider.ChangeObject) : Wrapper(), PresentableChange by change {
    override fun getUserObject(): Any = change
    override fun getPresentableName(): @Nls String = change.filePath.name
    override fun createProducer(project: Project?): DiffRequestProducer? = change.createDiffRequestProducer(project)
    override fun getTag(): ChangesBrowserNode.Tag? = change.tag
  }
}
