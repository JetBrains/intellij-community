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
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.ui.components.ActionLink;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageInfo2UsageAdapter;
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
  public SaveClusteringResultActionLink(@NotNull Project project, @NotNull ClusteringSearchSession session, @NotNull String fileName) {
    super(UsageViewBundle.message("similar.usages.internal.export.clustering.data"),
          (event) -> {
            List<UsageCluster> clusters = new ArrayList<>(session.getClusters());
            Task.Backgroundable loadMostCommonUsagePatternsTask = new Task.Backgroundable(project, UsageViewBundle.message(
              "similar.usages.internal.exporting.clustering.data.progress.title")) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                buildSessionDataFile(project, clusters, indicator, fileName);
              }
            };
            ProgressIndicator indicator = new BackgroundableProcessIndicator(loadMostCommonUsagePatternsTask);
            indicator.setIndeterminate(false);
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(loadMostCommonUsagePatternsTask, indicator);
          });
  }

  private static void createScratchFile(@NotNull Project project, @NotNull String fileContent, @NotNull String fileName)
    throws IOException {
    ScratchFileService fileService = ScratchFileService.getInstance();
    VirtualFile scratchFile = fileService.findFile(RootType.findById("scratches"), fileName + ".json",
                                                   ScratchFileService.Option.create_new_always);
    PsiFile psiFile = PsiManager.getInstance(project).findFile(scratchFile);
    assert psiFile != null;
    final Document document = PsiDocumentManager.getInstance(project).getDocument(psiFile);
    if (document != null) {
      document.insertString(document.getTextLength(), fileContent);
      PsiDocumentManager.getInstance(project).commitDocument(document);
    }
    CodeStyleManager.getInstance(project).reformatText(psiFile, psiFile.getTextRange().getStartOffset(), psiFile.getTextRange().getEndOffset());
  }

  private static void buildSessionDataFile(@NotNull Project project,
                                           @NotNull List<UsageCluster> clusters,
                                           @NotNull ProgressIndicator indicator, @NotNull String fileName) {
    int counter = 0;
    StringBuilder sb = new StringBuilder();
    sb.append("[");
    boolean isFirst = true;
    for (UsageCluster cluster : clusters) {
      indicator.setFraction(counter++ / (double)clusters.size());
      HashSet<SimilarUsage> usages = new HashSet<>(cluster.getUsages());
      for (SimilarUsage usage : usages) {
        if (usage instanceof UsageInfo2UsageAdapter) {
          if (isFirst) {
            isFirst = false;
          }
          else {
            sb.append(",\n");
          }
          indicator.checkCanceled();
          Ref<PsiElement> elementRef = new Ref<>();
          Ref<String> fileNameRef = new Ref<>();
          Ref<String> usageLineSnippet = new Ref<>("");
          ApplicationManager.getApplication().runReadAction(() -> {
            elementRef.set(((UsageInfo2UsageAdapter)usage).getElement());
            VirtualFile containingVirtualFile = elementRef.get().getContainingFile().getVirtualFile();
            assert containingVirtualFile != null;
            VirtualFile rootForFile = ProjectFileIndex.getInstance(project).getSourceRootForFile(containingVirtualFile);
            if (rootForFile != null) {
              fileNameRef.set(VfsUtilCore.getRelativePath(containingVirtualFile, rootForFile));
              PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
              Document doc = docManager.getDocument(elementRef.get().getContainingFile());
              if (doc == null) {
                return;
              }
              int usageStartLineNumber = doc.getLineNumber(elementRef.get().getTextRange().getStartOffset());
              int usageEndLineNumber = doc.getLineNumber(elementRef.get().getTextRange().getEndOffset());
              usageLineSnippet.set(doc.getText(new TextRange(doc.getLineStartOffset(usageStartLineNumber),
                                                             doc.getLineEndOffset(Math.min(usageEndLineNumber, doc.getLineCount() - 1)))));
            }
          });
          PsiElement element = elementRef.get();
          String sourceFileName = fileNameRef.get();
          String fileNameBase = sourceFileName +
                                ":" +
                                element.getTextRange().getStartOffset();
          sb.append("{\"filename\":").append("\"").append(fileNameBase).append("\",\n");
          sb.append("\"snippet\":").append("\"").append(StringUtil.escapeChars(usageLineSnippet.get(), '\\', '"').trim()).append("\",\n");
          sb.append("\"cluster_number\": ").append(counter).append(",\n");
          sb.append("\"features\":").append(createJsonForFeatures(usage.getFeatures())).append("}");
        }
      }
    }
    sb.append("]");
    try {
      writeCommandAction(project).run(() -> {
        createScratchFile(project, sb.toString(), fileName);
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
                                                                                       @NotNull ClusteringSearchSession session,
                                                                                       @Nullable String fileName) {
    return Registry.is("similarity.import.clustering.results.action.enabled")
           ? new SaveClusteringResultActionLink(project, session,
                                                StringUtilRt.notNullize(fileName, "features"))
           : null;
  }
}
