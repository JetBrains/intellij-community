// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.find.findUsages.similarity;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.ui.components.ActionLink;
import com.intellij.usageView.UsageViewBundle;
import com.intellij.usages.UsageInfo2UsageAdapter;
import com.intellij.usages.similarity.clustering.ClusteringSearchSession;
import com.intellij.usages.similarity.clustering.UsageCluster;
import com.intellij.usages.similarity.usageAdapter.SimilarUsage;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.io.StringWriter;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.command.WriteCommandAction.writeCommandAction;

public class ExportClusteringResultActionLink extends ActionLink {
  public static final String FILENAME = "filename";
  public static final String CLUSTER_NUMBER = "cluster_number";
  public static final String SNIPPET = "snippet";
  public static final String FEATURES = "features";

  public ExportClusteringResultActionLink(@NotNull Project project, @NotNull ClusteringSearchSession session, @NotNull String fileName) {
    super(UsageViewBundle.message("similar.usages.internal.export.clustering.data"),
          (event) -> {
            List<UsageCluster> clusters = session.getClusters();
            Task.Backgroundable loadMostCommonUsagePatternsTask = new Task.Backgroundable(project, UsageViewBundle.message(
              "similar.usages.internal.exporting.clustering.data.progress.title")) {
              @Override
              public void run(@NotNull ProgressIndicator indicator) {
                try {
                  buildSessionDataFile(project, clusters, indicator, fileName);
                }
                catch (IOException e) {
                  throw new RuntimeException(e);
                }
              }
            };
            ProgressIndicator indicator = new BackgroundableProcessIndicator(loadMostCommonUsagePatternsTask);
            indicator.setIndeterminate(false);
            ProgressManager.getInstance().runProcessWithProgressAsynchronously(loadMostCommonUsagePatternsTask, indicator);
          });
  }

  private static void createScratchFile(@NotNull String fileContent, @NotNull String fileName)
    throws IOException {
    ScratchFileService fileService = ScratchFileService.getInstance();
    VirtualFile scratchFile = fileService.findFile(RootType.findById("scratches"), fileName + ".json",
                                                   ScratchFileService.Option.create_new_always);
    VfsUtil.saveText(scratchFile, fileContent);
  }

  private static void buildSessionDataFile(@NotNull Project project,
                                           @NotNull List<UsageCluster> clusters,
                                           @NotNull ProgressIndicator indicator, @NotNull String fileName) throws IOException {
    int counter = 0;
    StringWriter stringWriter = new StringWriter();
    JsonGenerator generator = new JsonFactory().createGenerator(stringWriter);
    generator.writeStartArray();
    for (UsageCluster cluster : clusters) {
      indicator.setFraction(counter++ / (double)clusters.size());
      Set<SimilarUsage> usages = cluster.getUsages();
      for (SimilarUsage usage : usages) {
        if (usage instanceof UsageInfo2UsageAdapter) {
          indicator.checkCanceled();
          PsiElement element = getElement((UsageInfo2UsageAdapter)usage);
          generator.writeStartObject();
          generator.writeStringField(FILENAME, getUsageId(element));
          generator.writeStringField(SNIPPET, getUsageLineSnippet(project, element));
          generator.writeNumberField(CLUSTER_NUMBER, counter);
          generator.writeObjectFieldStart(FEATURES);
          for (Object2IntMap.Entry<String> entry : usage.getFeatures().getBag().object2IntEntrySet()) {
            generator.writeNumberField(entry.getKey(), entry.getIntValue());
          }
          generator.writeEndObject();
          generator.writeEndObject();
        }
      }
    }
    generator.writeEndArray();
    generator.close();

    writeCommandAction(project).run(() -> {
      createScratchFile(stringWriter.toString(), fileName);
    });
  }

  public static @NotNull PsiElement getElement(@NotNull UsageInfo2UsageAdapter usage) {
    Ref<PsiElement> elementRef = new Ref<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      elementRef.set(usage.getElement());
    });
    return elementRef.get();
  }

  public static @NotNull String getUsageId(@NotNull PsiElement element) {
    Ref<String> fileNameRef = new Ref<>();
    Ref<TextRange> elementTextRange = new Ref<>();
    ApplicationManager.getApplication().runReadAction(() -> {
      VirtualFile containingVirtualFile = element.getContainingFile().getVirtualFile();
      assert containingVirtualFile != null;
      VirtualFile rootForFile = ProjectFileIndex.getInstance(element.getProject()).getSourceRootForFile(containingVirtualFile);
      if (rootForFile != null) {
        fileNameRef.set(VfsUtilCore.getRelativePath(containingVirtualFile, rootForFile));
        elementTextRange.set(element.getTextRange());
      }
    });
    TextRange range = elementTextRange.get();
    return fileNameRef.get() + ":" + (range != null ? range.getStartOffset() : 0);
  }

  private static @NotNull String getUsageLineSnippet(@NotNull Project project, @NotNull PsiElement element) {
    Ref<String> usageLineSnippet = new Ref<>("");
    ApplicationManager.getApplication().runReadAction(() -> {
      PsiDocumentManager docManager = PsiDocumentManager.getInstance(project);
      Document doc = docManager.getDocument(element.getContainingFile());
      if (doc != null) {
        int usageStartLineNumber = doc.getLineNumber(element.getTextRange().getStartOffset());
        int usageEndLineNumber = doc.getLineNumber(element.getTextRange().getEndOffset());
        usageLineSnippet.set(doc.getText(new TextRange(doc.getLineStartOffset(usageStartLineNumber),
                                                       doc.getLineEndOffset(Math.min(usageEndLineNumber, doc.getLineCount() - 1)))));
      }
    });
    return StringUtil.escapeChars(removeNewLines(usageLineSnippet.get()), '\\', '"').trim();
  }

  private static @NotNull String removeNewLines(@NotNull String snippet) {
    return snippet.replace("\n", " ").replace("\t", " ").replace("\r", " ");
  }
}
