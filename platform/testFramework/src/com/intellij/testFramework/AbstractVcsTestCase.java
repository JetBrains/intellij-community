/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.testFramework;

import com.intellij.execution.process.CapturingProcessHandler;
import com.intellij.execution.process.ProcessOutput;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diff.LineTokenizer;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.VcsConfiguration;
import com.intellij.openapi.vcs.VcsShowConfirmationOption;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiNamedElement;
import com.intellij.testFramework.builders.EmptyModuleFixtureBuilder;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.*;

/**
 * @author yole
 */
public class AbstractVcsTestCase {
  protected Project myProject;
  protected VirtualFile myWorkingCopyDir;
  protected File myClientBinaryPath;
  protected IdeaProjectTestFixture myProjectFixture;
  protected boolean myInitChangeListManager = true;

  protected ProcessOutput runClient(String exeName, @Nullable String stdin, @Nullable final File workingDir, String[] commandLine) throws IOException {
    final List<String> arguments = new ArrayList<String>();
    final File client = new File(myClientBinaryPath, SystemInfo.isWindows ? exeName + ".exe" : exeName);
    if (client.exists()) {
      arguments.add(client.toString());
    }
    else {
      // assume client is in path
      arguments.add(exeName);
    }
    Collections.addAll(arguments, commandLine);
    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    if (workingDir != null) {
      builder.directory(workingDir);
    }
    Process clientProcess = builder.start();

    if (stdin != null) {
      OutputStream outputStream = clientProcess.getOutputStream();
      try {
        byte[] bytes = stdin.getBytes();
        outputStream.write(bytes);
      }
      finally {
        outputStream.close();
      }
    }

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(60*1000);
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for VCS client to finish execution");
    }
    return result;
  }

  protected static ProcessOutput runArbitrary(final String command, final String[] args) throws IOException {
    final List<String> arguments = new ArrayList<String>();
    arguments.add(command);
    Collections.addAll(arguments, args);
    final ProcessBuilder builder = new ProcessBuilder().command(arguments);
    Process clientProcess = builder.start();

    CapturingProcessHandler handler = new CapturingProcessHandler(clientProcess, CharsetToolkit.getDefaultSystemCharset());
    ProcessOutput result = handler.runProcess(60*1000);
    if (result.isTimeout()) {
      throw new RuntimeException("Timeout waiting for VCS client to finish execution");
    }
    return result;
  }

  protected void initProject(final File clientRoot) throws Exception {
    final TestFixtureBuilder<IdeaProjectTestFixture> testFixtureBuilder = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder();
    myProjectFixture = testFixtureBuilder.getFixture();
    testFixtureBuilder.addModule(EmptyModuleFixtureBuilder.class).addContentRoot(clientRoot.toString());
    myProjectFixture.setUp();
    myProject = myProjectFixture.getProject();

    if (myInitChangeListManager) {
      ((ProjectComponent) ChangeListManager.getInstance(myProject)).projectOpened();
    }
    ((ProjectComponent) VcsDirtyScopeManager.getInstance(myProject)).projectOpened();

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        myWorkingCopyDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(clientRoot);
        assert myWorkingCopyDir != null;
      }
    });
  }

  protected void activateVCS(final String vcsName) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    vcsManager.setDirectoryMapping(myWorkingCopyDir.getPath(), vcsName);
    vcsManager.updateActiveVcss();

    AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
    Assert.assertEquals(1, vcsManager.getRootsUnderVcs(vcs).length);
  }

  protected VirtualFile createFileInCommand(final String name, @Nullable final String content) {
    return createFileInCommand(myWorkingCopyDir, name, content);
  }

  protected VirtualFile createFileInCommand(final VirtualFile parent, final String name, @Nullable final String content) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    new WriteCommandAction.Simple(myProject) {
      protected void run() throws Throwable {
        try {
          VirtualFile file = parent.createChildData(this, name);
          if (content != null) {
            file.setBinaryContent(CharsetToolkit.getUtf8Bytes(content));
          }
          result.set(file);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    return result.get();
  }

  protected VirtualFile createDirInCommand(final VirtualFile parent, final String name) {
    final Ref<VirtualFile> result = new Ref<VirtualFile>();
    new WriteCommandAction.Simple(myProject) {
      protected void run() throws Throwable {
        try {
          VirtualFile dir = parent.createChildDirectory(this, name);
          result.set(dir);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
    return result.get();
  }

  protected void tearDownProject() throws Exception {
    if (myProject != null) {
      ((ProjectComponent) VcsDirtyScopeManager.getInstance(myProject)).projectClosed();
      ((ProjectComponent) ChangeListManager.getInstance(myProject)).projectClosed();
      ((ProjectComponent) ProjectLevelVcsManager.getInstance(myProject)).projectClosed();
      myProject = null;
    }
    if (myProjectFixture != null) {
      myProjectFixture.tearDown();
      myProjectFixture = null;
    }
  }

  protected void setStandardConfirmation(final String vcsName, final VcsConfiguration.StandardConfirmation op,
                                         final VcsShowConfirmationOption.Value value) {
    ProjectLevelVcsManager vcsManager = ProjectLevelVcsManager.getInstance(myProject);
    final AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
    VcsShowConfirmationOption option = vcsManager.getStandardConfirmation(op, vcs);
    option.setValue(value);
  }

  protected static void verify(final ProcessOutput runResult) {
    Assert.assertEquals(runResult.getStderr(), 0, runResult.getExitCode());
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

  protected VcsDirtyScope getAllDirtyScope() {
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    dirtyScopeManager.markEverythingDirty();
    List<VcsDirtyScope> scopes = dirtyScopeManager.retrieveScopes().getScopes();
    Assert.assertEquals(1, scopes.size());
    return scopes.get(0);
  }

  protected VcsDirtyScope getDirtyScopeForFile(VirtualFile file) {
    VcsDirtyScopeManager dirtyScopeManager = VcsDirtyScopeManager.getInstance(myProject);
    dirtyScopeManager.retrieveScopes();  // ensure that everything besides the file is clean
    dirtyScopeManager.fileDirty(file);
    List<VcsDirtyScope> scopes = dirtyScopeManager.retrieveScopes().getScopes();
    Assert.assertEquals(1, scopes.size());
    return scopes.get(0);
  }

  protected void renameFileInCommand(final VirtualFile file, final String newName) {
    renameFileInCommand(myProject, file, newName);
  }

  public static void renameFileInCommand(final Project project, final VirtualFile file, final String newName) {
    new WriteCommandAction.Simple(project) {
      protected void run() throws Throwable {
        try {
          file.rename(this, newName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
  }

  protected void renamePsiInCommand(final PsiNamedElement element, final String newName) {
    new WriteCommandAction.Simple(myProject) {
      protected void run() throws Throwable {
        try {
          element.setName(newName);
        }
        catch (IncorrectOperationException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
  }

  protected void deleteFileInCommand(final VirtualFile file) {
    deleteFileInCommand(myProject, file);
  }

  public static void deleteFileInCommand(final Project project, final VirtualFile file) {
    new WriteCommandAction.Simple(project) {
      protected void run() throws Throwable {
        try {
          file.delete(this);
        }
        catch(IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }.execute();
  }

  public static void editFileInCommand(final Project project, final VirtualFile file, final String newContent) {
    new WriteCommandAction.Simple(project) {
      protected void run() throws Throwable {
        try {
          file.setBinaryContent(newContent.getBytes());
        }
        catch(IOException ex) {
          throw new RuntimeException(ex);
        }
      }
    }.execute();
  }

  protected void copyFileInCommand(final VirtualFile file, final String toName) {
    new WriteCommandAction.Simple(myProject) {
      protected void run() throws Throwable {
        try {
          file.copy(this, file.getParent(), toName);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
  }

  protected void moveFileInCommand(final VirtualFile file, final VirtualFile newParent) {
    moveFileInCommand(myProject, file, newParent);
  }

  public static void moveFileInCommand(final Project project, final VirtualFile file, final VirtualFile newParent) {
    new WriteCommandAction.Simple(project) {
      protected void run() throws Throwable {
        try {
          file.move(this, newParent);
        }
        catch (IOException e) {
          throw new RuntimeException(e);
        }
      }
    }.execute();
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

  private void verifyRevision(final ContentRevision beforeRevision, final String beforePath) {
    File beforeFile = new File(myWorkingCopyDir.getPath(), beforePath);
    String beforeFullPath = FileUtil.toSystemIndependentName(beforeFile.getPath());
    final String beforeRevPath = FileUtil.toSystemIndependentName(beforeRevision.getFile().getPath());
    Assert.assertTrue(beforeFullPath.equalsIgnoreCase(beforeRevPath));
  }

  public static void sortChanges(final List<Change> changes) {
    Collections.sort(changes, new Comparator<Change>() {
      public int compare(final Change o1, final Change o2) {
        final String p1 = FileUtil.toSystemIndependentName(ChangesUtil.getFilePath(o1).getPath());
        final String p2 = FileUtil.toSystemIndependentName(ChangesUtil.getFilePath(o2).getPath());
        return p1.compareTo(p2);
      }
    });
  }
}
