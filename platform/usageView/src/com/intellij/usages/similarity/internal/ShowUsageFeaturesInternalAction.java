// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages.similarity.internal;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.Editor;
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
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.Map;
import java.util.stream.Collectors;

import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

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
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }

  private static void calculateFeaturesForUsage(@NotNull Editor editor,
                                                @NotNull PsiFile file,
                                                @NotNull Project project,
                                                @NotNull Ref<? super PsiFile> featuresDump) {
    ProgressManager.getInstance().run(new Task.Modal(project, UsageViewBundle.message(
      "similar.usages.show.usage.features.action.calculating.usage.features.progress.title"), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final Bag features = new Bag();
        Ref<PsiElement> element = new Ref<>();
        ApplicationManager.getApplication().runReadAction(() -> {
          PsiReference referenceAt = file.findReferenceAt(editor.getCaretModel().getOffset());
          if (referenceAt == null) return;
          element.set(referenceAt.getElement());
          if (!element.isNull()) {
            UsageSimilarityFeaturesProvider.EP_NAME.forEachExtensionSafe(provider -> {
                                                                           features.addAll(provider.getFeatures(element.get()));
                                                                         }
            );
          }
        });
        if (element.isNull()) {
          return;
        }
        writeCommandAction(project).compute(() -> {
          ScratchFileService fileService = ScratchFileService.getInstance();
          try {
            VirtualFile scratchFile = fileService.findFile(RootType.findById("scratches"), getFeaturesFileName(file, element.get(), editor),
                                                           ScratchFileService.Option.create_new_always);
            PsiFile psiFile = PsiManager.getInstance(project).findFile(scratchFile);
            featuresDump.set(psiFile);
            final Document document = psiFile != null ? PsiDocumentManager.getInstance(project).getDocument(psiFile) : null;
            if (document != null) {
              document.insertString(document.getTextLength(), StringUtil.join(features.getBag().object2IntEntrySet().stream().sorted(
                Map.Entry.comparingByKey()).collect(Collectors.toList()), ",\n"));
              PsiDocumentManager.getInstance(project).commitDocument(document);
              psiFile.navigate(true);
            }
          }
          catch (IOException e) {
            throw new RuntimeException(e);
          }
          return true;
        });
      }
    });
  }

  @NotNull
  private static String getFeaturesFileName(@NotNull PsiFile file, @NotNull PsiElement element, @NotNull Editor editor) {
    Document document = PsiDocumentManager.getInstance(file.getProject()).getDocument(file);
    String fileName = file.getName();
    return "features" +
           fileName.substring(0, fileName.lastIndexOf('.')) +
           (document != null ? document.getLineNumber(editor.getCaretModel().getOffset()) : "") +
           "-" + element.getText() + "-" +
           ".txt";
  }
}
