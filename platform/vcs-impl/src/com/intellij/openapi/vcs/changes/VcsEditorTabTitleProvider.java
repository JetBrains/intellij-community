// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes;

import com.intellij.diff.editor.DiffRequestProcessorEditor;
import com.intellij.diff.impl.DiffRequestProcessor;
import com.intellij.diff.tools.combined.CombinedDiffModel;
import com.intellij.diff.tools.combined.CombinedDiffModelRepository;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.impl.EditorTabTitleProvider;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewVirtualFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.concurrency.EdtExecutorService;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.EDT;
import kotlin.jvm.functions.Function0;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.Future;
import java.util.function.Supplier;

import static com.intellij.openapi.vcs.changes.actions.diff.CombinedDiffPreviewKt.COMBINED_DIFF_PREVIEW_TAB_NAME;

public class VcsEditorTabTitleProvider implements EditorTabTitleProvider, DumbAware {

  @Nullable
  @Override
  public String getEditorTabTitle(@NotNull Project project, @NotNull VirtualFile file) {
    return getEditorTabName(project, file);
  }

  @Nullable
  @Override
  public String getEditorTabTooltipText(@NotNull Project project, @NotNull VirtualFile virtualFile) {
    return getEditorTabName(project, virtualFile);
  }

  @Nullable
  @NlsContexts.TabTitle
  private static String getEditorTabName(@NotNull Project project, @NotNull VirtualFile file) {
    if (!(file instanceof PreviewDiffVirtualFile) && !(file instanceof CombinedDiffPreviewVirtualFile)) return null;
    Supplier<@NlsContexts.TabTitle String> supplier = () -> {
      FileEditor[] editors = FileEditorManager.getInstance(project).getEditors(file);
      DiffRequestProcessorEditor editor = ContainerUtil.findInstance(editors, DiffRequestProcessorEditor.class);
      DiffRequestProcessor processor = editor != null ? editor.getProcessor() : null;
      if (file instanceof PreviewDiffVirtualFile) {
        return ((PreviewDiffVirtualFile)file).getProvider().getEditorTabName(processor);
      }
      else {
        String sourceId = ((CombinedDiffPreviewVirtualFile)file).getSourceId();
        CombinedDiffModel diffModel = project.getService(CombinedDiffModelRepository.class).findModel(sourceId);
        Function0<@NlsContexts.TabTitle String> tabNameSupplier =
          diffModel != null ? diffModel.getContext().getUserData(COMBINED_DIFF_PREVIEW_TAB_NAME) : null;
        return tabNameSupplier != null ? tabNameSupplier.invoke() : null;
      }
    };
    if (EDT.isCurrentThreadEdt()) return supplier.get();
    Future<@NlsContexts.TabTitle String> future = EdtExecutorService.getInstance().submit(supplier::get, ModalityState.any());
    return ProgressIndicatorUtils.awaitWithCheckCanceled(future);
  }
}
