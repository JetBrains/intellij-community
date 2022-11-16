// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.testframework.actions;

import com.intellij.diff.DiffContentFactory;
import com.intellij.diff.chains.DiffRequestChain;
import com.intellij.diff.chains.DiffRequestProducer;
import com.intellij.diff.chains.DiffRequestProducerException;
import com.intellij.diff.chains.SimpleDiffRequestChain;
import com.intellij.diff.contents.DiffContent;
import com.intellij.diff.requests.DiffRequest;
import com.intellij.diff.requests.SimpleDiffRequest;
import com.intellij.diff.util.DiffPlaces;
import com.intellij.diff.util.DiffUserDataKeys;
import com.intellij.execution.ExecutionBundle;
import com.intellij.execution.Location;
import com.intellij.execution.testframework.AbstractTestProxy;
import com.intellij.execution.testframework.stacktrace.DiffHyperlink;
import com.intellij.openapi.ListSelection;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.PlainTextFileType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.NewVirtualFileSystem;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.io.URLUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class TestDiffRequestProcessor {
  @NotNull
  public static DiffRequestChain createRequestChain(@Nullable Project project, @NotNull ListSelection<? extends DiffHyperlink> requests) {
    ListSelection<DiffRequestProducer> producers = requests.map(hyperlink -> new DiffHyperlinkRequestProducer(project, hyperlink));

    SimpleDiffRequestChain chain = SimpleDiffRequestChain.fromProducers(producers);
    chain.putUserData(DiffUserDataKeys.PLACE, DiffPlaces.TESTS_FAILED_ASSERTIONS);
    chain.putUserData(DiffUserDataKeys.DO_NOT_IGNORE_WHITESPACES, true);
    chain.putUserData(DiffUserDataKeys.DIALOG_GROUP_KEY,
                      "#com.intellij.execution.junit2.states.ComparisonFailureState$DiffDialog");  // NON-NLS
    return chain;
  }

  private static class DiffHyperlinkRequestProducer implements DiffRequestProducer {
    private final Project myProject;
    private final DiffHyperlink myHyperlink;

    private DiffHyperlinkRequestProducer(@Nullable Project project, @NotNull DiffHyperlink hyperlink) {
      myProject = project;
      myHyperlink = hyperlink;
    }

    @Override
    public @Nls @NotNull String getName() {
      String testName = myHyperlink.getTestName();
      if (testName != null) return testName;
      return myHyperlink.getDiffTitle();
    }

    @Override
    public @Nullable FileType getContentType() {
      VirtualFile file = findFile(myHyperlink.getFilePath());
      return file != null ? file.getFileType() : PlainTextFileType.INSTANCE;
    }

    @Override
    @NotNull
    public DiffRequest process(@NotNull UserDataHolder context,
                               @NotNull ProgressIndicator indicator) throws DiffRequestProducerException {
      String windowTitle = myHyperlink.getDiffTitle();
      AbstractTestProxy testProxy = myHyperlink.getTestProxy();

      String text1 = myHyperlink.getLeft();
      String text2 = myHyperlink.getRight();
      VirtualFile file1 = findFile(myHyperlink.getFilePath());
      if (file1 == null && testProxy != null) {
        file1 = ReadAction.compute(() -> extractInjection(testProxy));
      }
      VirtualFile file2 = findFile(myHyperlink.getActualFilePath());

      DiffContent content1 = createContentWithTitle(myProject, text1, file1, file2);
      DiffContent content2 = createContentWithTitle(myProject, text2, file2, file1);

      String title1 = file1 != null ? ExecutionBundle.message("diff.content.expected.title.with.file.url", file1.getPresentableUrl())
                                    : ExecutionBundle.message("diff.content.expected.title");
      String title2 = file2 != null ? ExecutionBundle.message("diff.content.actual.title.with.file.url", file2.getPresentableUrl())
                                    : ExecutionBundle.message("diff.content.actual.title");

      return new SimpleDiffRequest(windowTitle, content1, content2, title1, title2);
    }

    @Nullable
    private VirtualFile extractInjection(AbstractTestProxy testProxy) {
      if (myProject == null) return null;
      Location<?> location = testProxy.getLocation(myProject, GlobalSearchScope.projectScope(myProject));
      if (location == null) return null;
      TestDiffProvider testDiffProvider = TestDiffProvider.TEST_DIFF_PROVIDER_LANGUAGE_EXTENSION.forLanguage(
        location.getPsiElement().getLanguage()
      );
      String stackTrace = testProxy.getStacktrace();
      if (stackTrace == null) return null;
      PsiElement injectionLiteral = testDiffProvider.findExpected(myProject, testProxy.getStacktrace());
      if (injectionLiteral == null) return null;
      return injectionLiteral.getContainingFile().getVirtualFile();
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;
      DiffHyperlinkRequestProducer producer = (DiffHyperlinkRequestProducer)o;
      return Objects.equals(myHyperlink, producer.myHyperlink);
    }

    @Override
    public int hashCode() {
      return Objects.hash(myHyperlink);
    }
  }

  @Nullable
  private static VirtualFile findFile(@Nullable String path) {
    if (path == null) return null;
    NewVirtualFileSystem fs = path.contains(URLUtil.JAR_SEPARATOR) ? JarFileSystem.getInstance() : LocalFileSystem.getInstance();
    return fs.refreshAndFindFileByPath(path);
  }

  @NotNull
  private static DiffContent createContentWithTitle(@Nullable Project project,
                                                    @NotNull String content,
                                                    @Nullable VirtualFile contentFile,
                                                    @Nullable VirtualFile highlightFile) {
    if (contentFile != null) {
      return DiffContentFactory.getInstance().create(project, contentFile);
    }
    else {
      return DiffContentFactory.getInstance().create(project, content, highlightFile);
    }
  }
}
