// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.vcs;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.annotate.AnnotationProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.committed.CommittedChangesCache;
import com.intellij.openapi.vcs.impl.ProjectLevelVcsManagerImpl;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.EdtTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.RunAll;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.ExceptionUtil;
import com.intellij.util.ThrowableRunnable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;


@SuppressWarnings("TestOnlyProblems")
public abstract class AbstractVcsTestCase {
  protected boolean myTraceClient;
  protected Project myProject;
  protected VirtualFile myWorkingCopyDir;
  protected File myClientBinaryPath;
  protected IdeaProjectTestFixture myProjectFixture;

  protected TestClientRunner createClientRunner() {
    return createClientRunner(null);
  }

  protected TestClientRunner createClientRunner(@Nullable Map<String, String> clientEnvironment) {
    return new TestClientRunner(myTraceClient, myClientBinaryPath, clientEnvironment);
  }

  public void setVcsMappings(VcsDirectoryMapping... mappings) {
    setVcsMappings(Arrays.asList(mappings));
  }

  protected void setVcsMappings(List<VcsDirectoryMapping> mappings) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMappings(mappings);
    ChangeListManagerImpl.getInstanceImpl(myProject).waitUntilRefreshed();
  }

  protected void refreshVfs() {
    EdtTestUtil.runInEdtAndWait(() -> myWorkingCopyDir.refresh(false, true));
  }

  protected void initProject(@NotNull File clientRoot, String testName) throws Exception {
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder(testName);
    myProjectFixture = testFixtureBuilder.getFixture();
    testFixtureBuilder.addModule(EmptyModuleFixtureBuilder.class).addContentRoot(clientRoot.toString());
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();
    PlatformTestUtil.getOrCreateProjectBaseDir(myProject);

    projectCreated();

    myWorkingCopyDir = Objects.requireNonNull(VfsUtil.createDirectories(clientRoot.getPath()));
    ProjectLevelVcsManagerImpl.getInstanceImpl(myProject).waitForInitialized();
  }

  protected void projectCreated() {
  }

  protected void activateVCS(final String vcsName) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMapping(myWorkingCopyDir.getPath(), vcsName);

    AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
    Assert.assertEquals(1, vcsManager.getRootsUnderVcs(vcs).length);
  }

  public VirtualFile createFileInCommand(@NotNull String name, final @Nullable String content) {
    return createFileInCommand(myWorkingCopyDir, name, content);
  }

  public VirtualFile createFileInCommand(@NotNull VirtualFile parent, @NotNull String name, final @Nullable String content) {
    return VcsTestUtil.createFile(myProject, parent, name, content);
  }

  public VirtualFile createDirInCommand(final VirtualFile parent, final String name) {
    return VcsTestUtil.findOrCreateDir(myProject, parent, name);
  }

  protected void tearDownProject() throws Exception {
    new RunAll(
      () -> {
        if (myProject != null) {
          ChangeListManagerImpl.getInstanceImpl(myProject).stopEveryThingIfInTestMode();
          CommittedChangesCache.getInstance(myProject).clearCaches(EmptyRunnable.INSTANCE);
          myProject = null;
        }
      },
      () -> {
        if (myProjectFixture != null) {
          myProjectFixture.tearDown();
          myProjectFixture = null;
        }
      })
      .run();
  }

  public void setStandardConfirmation(final String vcsName, final VcsConfiguration.StandardConfirmation op,
                                             final VcsShowConfirmationOption.Value value) {
    setStandardConfirmation(myProject, vcsName, op, value);
  }

  public static void setStandardConfirmation(Project project, String vcsName, VcsConfiguration.StandardConfirmation op,
                                             VcsShowConfirmationOption.Value value) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(project);
    AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
    VcsShowConfirmationOption option = vcsManager.getStandardConfirmation(op, vcs);
    option.setValue(value);
  }

  public static void verify(final ProcessOutput runResult) {
    Assert.assertEquals(runResult.getStdout() + "\n---\n" + runResult.getStderr(), 0, runResult.getExitCode());
  }

  protected static void verify(final ProcessOutput runResult, final String... stdoutLines) {
    verify(runResult, false, stdoutLines);
  }

  protected static void verifySorted(final ProcessOutput runResult, final String... stdoutLines) {
    verify(runResult, true, stdoutLines);
  }

  private static void verify(final ProcessOutput runResult, final boolean sorted, final String... stdoutLines) {
    verify(runResult);
    final String[] lines = new LineTokenizer(runResult.getStdout()).execute();
    if (sorted) {
      Arrays.sort(lines);
    }
    Assert.assertEquals(runResult.getStdout(), stdoutLines.length, lines.length);
    for(int i=0; i<stdoutLines.length; i++) {
      Assert.assertEquals(stdoutLines [i], compressWhitespace(lines [i]));
    }
  }

  private static String compressWhitespace(String line) {
    while(line.indexOf("  ") > 0) {
      line = line.replace("  ", " ");
    }
    return line.trim();
  }

  protected void renameFileInCommand(final VirtualFile file, final String newName) {
    VcsTestUtil.renameFileInCommand(myProject, file, newName);
  }

  public void deleteFileInCommand(final VirtualFile file) {
    VcsTestUtil.deleteFileInCommand(myProject, file);
  }

  public void editFileInCommand(final VirtualFile file, final String newContent) {
    VcsTestUtil.editFileInCommand(myProject, file, newContent);
  }

  protected @NotNull VirtualFile copyFileInCommand(@NotNull VirtualFile file, final String toName) {
    final AtomicReference<VirtualFile> res = new AtomicReference<>();
    WriteCommandAction.writeCommandAction(myProject).run(() -> {
      try {
        res.set(file.copy(this, file.getParent(), toName));
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    });
    return res.get();
  }

  protected void moveFileInCommand(final VirtualFile file, final VirtualFile newParent) {
    VcsTestUtil.moveFileInCommand(myProject, file, newParent);
  }

  protected void verifyChange(final Change c, final String beforePath, final String afterPath) {
    if (beforePath == null) {
      Assert.assertNull(c.getBeforeRevision());
    }
    else {
      verifyRevision(c.getBeforeRevision(), beforePath);
    }
    if (afterPath == null) {
      Assert.assertNull(c.getAfterRevision());
    }
    else {
      verifyRevision(c.getAfterRevision(), afterPath);
    }
  }

  public VirtualFile getWorkingCopyDir() {
    return myWorkingCopyDir;
  }

  private void verifyRevision(final ContentRevision beforeRevision, final String beforePath) {
    File beforeFile = new File(myWorkingCopyDir.getPath(), beforePath);
    String beforeFullPath = FileUtil.toSystemIndependentName(beforeFile.getPath());
    final String beforeRevPath = FileUtil.toSystemIndependentName(beforeRevision.getFile().getPath());
    Assert.assertTrue(beforeFullPath + "!=" + beforeRevPath,  beforeFullPath.equalsIgnoreCase(beforeRevPath));
  }

  public FileAnnotation createTestAnnotation(@NotNull AnnotationProvider provider, VirtualFile file) throws VcsException {
    final FileAnnotation annotation = provider.annotate(file);
    Disposer.register(myProject, annotation::dispose);
    return annotation;
  }

  public void setFileText(final @NotNull VirtualFile file, final @NotNull String text) throws IOException {
    HeavyPlatformTestCase.setFileText(file, text);
  }

  public static void setBinaryContent(final VirtualFile file, final byte[] content) {
    HeavyPlatformTestCase.setBinaryContent(file, content);
  }

  public static <T extends Throwable> void runWithRetries(ThrowableRunnable<T> task) {
    int attempts = 0;
    while (true) {
      try {
        attempts++;
        task.run();
        return;
      }
      catch (Throwable e) {
        if (attempts >= 3) {
          ExceptionUtil.rethrow(e);
        }
      }
    }
  }
}
