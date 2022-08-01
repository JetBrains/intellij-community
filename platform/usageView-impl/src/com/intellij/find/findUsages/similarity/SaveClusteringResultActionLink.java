// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.intellij.ide.scratch.RootType;
import com.intellij.ide.scratch.ScratchFileService;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.progress.impl.BackgroundableProcessIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.components.ActionLink;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.impl.UsageViewImpl;
import com.intellij.usages.similarity.bag.Bag;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.Collectors;

import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;


public class SaveClusteringResultActionLink extends ActionLink {
  public SaveClusteringResultActionLink(Project project, ClusteringSearchSession session) {
    super(UsageViewBundle.message("similar.usages.internal.export.clustering.data"),
          (event) -> {
            assert session != null;
            List<UsageCluster> clusters = new ArrayList<>(session.getClusters());
            Task.Backgroundable loadMostCommonUsagePatternsTask = new Task.Backgroundable(project, UsageViewBundle.message(
              "similar.usages.internal.exporting.clustering.data.progress.title")) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                buildSessionDataFile(project, clusters, indicator);
              }
            };
            ProgressIndicator indicator = new BackgroundableProcessIndicator(loadMostCommonUsagePatternsTask);
            indicator.setIndeterminate(false);
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(loadMostCommonUsagePatternsTask, indicator);
          });
  }

  private static void createScratchFile(@NotNull Project project, @NotNull String fileContent) throws IOException {
    ScratchFileService fileService = ScratchFileService.getInstance();
    VirtualFile scratchFile = fileService.findFile(RootType.findById("scratches"), "features",
                                                   ScratchFileService.Option.create_new_always);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(scratchFile);
    final Document document = psiFile != null ? PsiDocumentManager.getInstance(project).getDocument(psiFile) : null;
    if (document != null) {
      document.insertString(document.getTextLength(), fileContent);
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
  }

  private static void buildSessionDataFile(Project project, List<UsageCluster> clusters, @NotNull ProgressIndicator indicator) {


    int counter = 0;
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    for (UsageCluster cluster : clusters) {
      indicator.setFraction(counter++ / (double)clusters.size());
      HashSet<SimilarUsage> usages = new HashSet<>(cluster.getUsages());
      for (SimilarUsage usage : usages) {
        if (usage instanceof UsageInfo2UsageAdapter) {
          indicator.checkCanceled();
          Ref<PsiElement> elementRef = new Ref<>();
          Ref<String> fileNameRef = new Ref<>();
          ApplicationManager.getApplication().runReadAction(() -> {
            elementRef.set(((UsageInfo2UsageAdapter)usage).getElement());
            fileNameRef.set(elementRef.get().getContainingFile().getVirtualFile().getName());
          });
          PsiElement element = elementRef.get();
          String fileName = fileNameRef.get();
          String fileNameBase = fileName.substring(0, fileName.indexOf('.')) +
                                ":" +
                                element.getTextRange().getStartOffset();

          sb.append("{\"filename\":").append("\"").append(fileNameBase).append("\",\n");
          sb.append("\"features\":").append(createJsonForFeatures(usage.getFeatures())).append("},");
        }
      }
    }
    sb.append("]");
    try {
      writeCommandAction(project).run(() -> {
        createScratchFile(project, sb.toString());
      });
    }
    catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  private static @NotNull String createJsonForFeatures(@NotNull Bag bag) {
    return bag.getBag().object2IntEntrySet().stream().map(entry -> "\"" + entry.getKey() + "\":" + entry.getIntValue())
      .collect(Collectors.joining(",\n", "{", "}"));
  }

  static @Nullable SaveClusteringResultActionLink getInternalSaveClusteringResultsLink(@NotNull Project project,
                                                                                       @NotNull UsageViewImpl usageView) {
    return Registry.is("similarity.import.clustering.results.action.enabled") ? new SaveClusteringResultActionLink(project,
                                                                                                                   MostCommonUsagePatternsComponent.findClusteringSessionInUsageView(
                                                                                                                     usageView)) : null;
  }
}
