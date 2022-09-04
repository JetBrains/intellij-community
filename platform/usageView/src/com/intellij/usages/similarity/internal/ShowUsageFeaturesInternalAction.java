// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.internal;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.*;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.features.UsageSimilarityFeaturesProvider;
import com.intellij.util.LocalTimeCounter;
import org.jetbrains.annotations.NotNull;

import static com.intellij.openapi.application.ActionsKt.runWriteAction;

public class ShowUsageFeaturesInternalAction extends AnAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    Editor editor = e.getData(CommonDataKeys.EDITOR);
    if (editor == null) return;
    final PsiFile file = e.getData(CommonDataKeys.PSI_FILE);
    if (file == null) return;
    final Project project = e.getProject();
    assert project != null;
    final VirtualFile projectDir = ProjectUtil.guessProjectDir(project);
    assert projectDir != null;
    PsiDirectory directory = PsiManager.getInstance(project).findDirectory(projectDir);
    assert directory != null;
    final Ref<PsiFile> featuresDump = new Ref<>();
    calculateFeaturesForUsage(editor, file, project, featuresDump);
    createFileWithFeatures(directory, featuresDump).navigate(true);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static void calculateFeaturesForUsage(@NotNull Editor editor,
                                                @NotNull PsiFile file,
                                                @NotNull Project project,
                                                @NotNull Ref<PsiFile> featuresDump) {
    ProgressManager.getInstance().run(new Task.Modal(project, UsageViewBundle.message(
      "similar.usages.show.usage.features.action.calculating.usage.features.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final Bag features = new Bag();
        ApplicationManager.getApplication().runReadAction(() -> {
          PsiElement element = file.findElementAt(editor.getCaretModel().getOffset());
          if (element != null) {
            UsageSimilarityFeaturesProvider.EP_NAME.forEachExtensionSafe(provider -> {
              features.addAll(provider.getFeatures(element));
            });
          }
        });
        featuresDump.set(
          PsiFileFactory.getInstance(project).createFileFromText("features" + System.currentTimeMillis(), PlainTextFileType.INSTANCE,
                                                                 StringUtil.join(
                                                                   features.getBag().object2IntEntrySet(), ",\n"),
                                                                 LocalTimeCounter.currentTime(), false, false));
      }
    });
  }

  private static @NotNull PsiFile createFileWithFeatures(@NotNull PsiDirectory directory, @NotNull Ref<PsiFile> featuresDumpFile) {
    return runWriteAction(() ->
                          {
                            final PsiElement featureDumpFile = directory.add(featuresDumpFile.get());
                            assert featureDumpFile instanceof PsiFile;
                            return (PsiFile)featureDumpFile;
                          }
    );
  }
}
