// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diagnostic.rethrowControlFlowException
import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.FileStatus
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserChangeNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNodeRenderer
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserStructuralNode
import com.intellij.openapi.vcs.changes.ui.ChangesViewModelBuilder
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.transformer.TextPresentationTransformers
import com.intellij.platform.backend.navigation.NavigationRequest
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.ui.SimpleTextAttributes
import java.util.concurrent.Callable

internal class StructuralChangesViewModifier(private val project: Project) : ChangesViewModifier {
  override fun modifyTreeModelBuilder(builder: ChangesViewModelBuilder) {
    if (builder !is TreeModelBuilder) return
    project.service<StructuralChangesViewUpdates>()
    if (!StructuralChangesViewSettings.isEnabled(project)) return

    val changeNodes = builder.myRoot.traverse().filter(ChangesBrowserChangeNode::class.java).toList()
    for (node in changeNodes) {
      ProgressManager.checkCanceled()
      val change = node.getUserObject()
      val afterRevision = change.afterRevision as? CurrentContentRevision ?: continue
      val path = afterRevision.file
      if (path.isDirectory || path.fileType.isBinary) continue
      val file = path.virtualFile ?: continue
      try {
        val beforeRevision = change.beforeRevision
        val baseText: String? = beforeRevision?.content
        if (beforeRevision != null && baseText == null) continue
        if (baseText != null && baseText.length > MAX_CONTENT_LENGTH) continue
        val elements = ReadAction.nonBlocking(Callable {
          if (DumbService.isDumb(project) || !file.isValid) return@Callable emptyList()
          val document = FileDocumentManager.getInstance().getDocument(file) ?: return@Callable emptyList()
          val afterText = document.immutableCharSequence.toString()
          if (afterText.length > MAX_CONTENT_LENGTH) return@Callable emptyList()
          val baseline = baseText?.let { TextPresentationTransformers.fromPersistent(it, file).toString() }
          val beforeText = getBeforeContent(change, baseline)
          if (beforeRevision != null && beforeText == null) return@Callable emptyList()
          val original = PsiManager.getInstance(project).findFile(file) ?: return@Callable emptyList()
          val before = beforeText?.let { StructureChangeCollector.createSnapshot(original, it) ?: return@Callable emptyList() }
          val after = StructureChangeCollector.createSnapshot(original, afterText) ?: return@Callable emptyList()
          StructureChangeCollector.collect(before, after).map { createNode(file, it) }
        }).expireWith(project).executeSynchronously()
        for (element in elements) {
          node.add(element)
        }
      }
      catch (e: Exception) {
        rethrowControlFlowException(e)
        logger<StructuralChangesViewModifier>().warn("Cannot collect structural changes for ${path.path}", e)
      }
    }
  }

  private fun getBeforeContent(change: Change, baseText: String?): String? {
    if (baseText == null) return null
    if (change is ChangeListChange) {
      val tracker = PartialChangesUtil.getPartialTracker(project, change)
      if (tracker != null) {
        return tracker.getChangesToBeCommitted(Side.RIGHT, listOf(change.changeListId), false)
      }
    }
    return baseText
  }

  private fun createNode(file: VirtualFile, element: StructureChange): ChangesBrowserNode<*> {
    val node = StructuralChangeNode(project, file, element)
    for (child in element.children) {
      node.add(createNode(file, child))
    }
    return node
  }

  private companion object {
    const val MAX_CONTENT_LENGTH = 1_000_000
  }
}

private class StructuralChangeNode(
  project: Project,
  file: VirtualFile,
  private val element: StructureChange,
) : ChangesBrowserStructuralNode<String>(element.id) {
  private val navigatable = element.offset?.let { OpenFileDescriptor(project, file, it) }

  override fun getNavigatable(): Navigatable? = navigatable

  override fun getPreviewNavigationRequest(): NavigationRequest? {
    val descriptor = navigatable ?: return null
    return NavigationRequest.sourceNavigationRequest(descriptor.project, descriptor.file, descriptor.offset)
  }

  override fun getTextPresentation(): String = element.text

  override fun render(renderer: ChangesBrowserNodeRenderer, selected: Boolean, expanded: Boolean, hasFocus: Boolean) {
    val style = if (element.status == FileStatus.DELETED) SimpleTextAttributes.STYLE_STRIKEOUT else SimpleTextAttributes.STYLE_PLAIN
    renderer.append(element.text, SimpleTextAttributes(style, element.status.color))
    renderer.icon = element.icon
  }
}

@Service(Service.Level.PROJECT)
internal class StructuralChangesViewUpdates(private val project: Project) : Disposable {
  init {
    project.messageBus.connect(this).subscribe(PsiModificationTracker.TOPIC, PsiModificationTracker.Listener { refresh() })
    RegistryManager.getInstance().get(StructuralChangesViewSettings.REGISTRY_KEY).addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) {
        project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
      }
    }, this)
  }

  private fun refresh() {
    if (StructuralChangesViewSettings.isEnabled(project)) {
      project.messageBus.syncPublisher(ChangesViewModifier.TOPIC).updated()
    }
  }

  override fun dispose() {}
}
