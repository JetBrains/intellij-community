// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.codeInsight.hints

import com.intellij.openapi.Disposable
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.ex.util.EditorUtil.disposeWithEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.ProjectLevelVcsManager
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.annotate.AnnotationsPreloader
import com.intellij.openapi.vcs.annotate.FileAnnotation
import com.intellij.openapi.vcs.annotate.LineAnnotationAspect
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.layout.*
import com.intellij.vcs.CacheableAnnotationProvider
import javax.swing.JComponent

private fun isCodeAuthorEnabledInRegistry(): Boolean = Registry.`is`("vcs.code.author.inlay.hints")

private fun isCodeAuthorEnabledInSettings(): Boolean =
  InlayHintsProviderExtension.findProviders()
    .filter { it.provider.key == KEY }
    .any { InlayHintsSettings.instance().hintsShouldBeShown(KEY, it.language) }

internal fun isCodeAuthorInlayHintsEnabled(): Boolean = isCodeAuthorEnabledInRegistry() && isCodeAuthorEnabledInSettings()

internal fun refreshCodeAuthorInlayHints() {
  if (!isCodeAuthorInlayHintsEnabled()) return

  InlayHintsPassFactory.forceHintsUpdateOnNextPass()
}

private val KEY = SettingsKey<NoSettings>("vcs.code.author")

abstract class VcsCodeAuthorInlayHintsProvider : InlayHintsProvider<NoSettings> {
  override fun getCollectorFor(file: PsiFile, editor: Editor, settings: NoSettings, sink: InlayHintsSink): InlayHintsCollector? {
    if (!isCodeAuthorEnabledInRegistry()) return null

    val virtualFile = file.virtualFile ?: return null
    val annotation = getAnnotation(file.project, virtualFile, editor) ?: return null
    val authorAspect = annotation.aspects.find { it.id == LineAnnotationAspect.AUTHOR } ?: return null

    return VcsCodeAuthorInlayHintsCollector(editor, authorAspect, this::isAccepted)
  }

  protected abstract fun isAccepted(element: PsiElement): Boolean

  override fun createSettings(): NoSettings = NoSettings()
  override val isVisibleInSettings: Boolean get() = isCodeAuthorEnabledInRegistry()

  override val name: String get() = message("label.code.author.inlay.hints")
  override val key: SettingsKey<NoSettings> get() = KEY
  override val previewText: String? get() = null

  override fun createConfigurable(settings: NoSettings): ImmediateConfigurable =
    object : ImmediateConfigurable {
      override fun createComponent(listener: ChangeListener): JComponent = panel {}
    }
}

private val VCS_CODE_AUTHOR_ANNOTATION = Key.create<FileAnnotation>("Vcs.CodeAuthor.Annotation")

private fun getAnnotation(project: Project, file: VirtualFile, editor: Editor): FileAnnotation? {
  editor.getUserData(VCS_CODE_AUTHOR_ANNOTATION)?.let { return it }

  val vcs = ProjectLevelVcsManager.getInstance(project).getVcsFor(file) ?: return null
  val provider = vcs.annotationProvider as? CacheableAnnotationProvider ?: return null
  val annotation = provider.getFromCache(file) ?: return null

  val annotationDisposable = Disposable {
    unregisterAnnotation(file, annotation)
    annotation.dispose()
  }
  annotation.setCloser {
    editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, null)
    Disposer.dispose(annotationDisposable)

    project.service<AnnotationsPreloader>().schedulePreloading(file)
  }
  annotation.setReloader { annotation.close() }

  editor.putUserData(VCS_CODE_AUTHOR_ANNOTATION, annotation)
  registerAnnotation(file, annotation)
  disposeWithEditor(editor, annotationDisposable)

  return annotation
}

private fun registerAnnotation(file: VirtualFile, annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.registerAnnotation(file, annotation)

private fun unregisterAnnotation(file: VirtualFile, annotation: FileAnnotation) =
  ProjectLevelVcsManager.getInstance(annotation.project).annotationLocalChangesListener.unregisterAnnotation(file, annotation)
