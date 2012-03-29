/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;

@NonNls public class PsiTestUtil {
  public static VirtualFile createTestProjectStructure(Project project,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<File> filesToDelete) throws Exception {
    return createTestProjectStructure(project, module, rootPath, filesToDelete, true);
  }

  public static VirtualFile createTestProjectStructure(Project project, Module module, Collection<File> filesToDelete)
    throws Exception {
    return createTestProjectStructure(project, module, null, filesToDelete, true);
  }

  public static VirtualFile createTestProjectStructure(Project project,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<File> filesToDelete,
                                                       boolean addProjectRoots) throws Exception {
    VirtualFile vDir = createTestProjectStructure(module, rootPath, filesToDelete, addProjectRoots);

    PsiDocumentManager.getInstance(project).commitAllDocuments();

    return vDir;
  }

  public static VirtualFile createTestProjectStructure(final Module module,
                                                       final String rootPath,
                                                       final Collection<File> filesToDelete,
                                                       final boolean addProjectRoots) throws Exception {
    return createTestProjectStructure("unitTest",module, rootPath, filesToDelete, addProjectRoots);
  }
  public static VirtualFile createTestProjectStructure(String tempName,
                                                       final Module module,
                                                       final String rootPath,
                                                       final Collection<File> filesToDelete,
                                                       final boolean addProjectRoots) throws Exception {
    File dir = FileUtil.createTempDirectory(tempName, null);
    filesToDelete.add(dir);

    final VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assert vDir.isDirectory(): vDir;

    final Exception[] exception = {null};
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        if (rootPath != null) {
          VirtualFile vDir1 = LocalFileSystem.getInstance().findFileByPath(rootPath.replace(File.separatorChar, '/'));
          if (vDir1 == null) {
            exception[0] = new Exception(rootPath + " not found");
            return;
          }
          try {
            VfsUtil.copyDirectory(null, vDir1, vDir, null);
          }
          catch (IOException e) {
            exception[0] = e;
            return;
          }
        }

        if (addProjectRoots) {
          addSourceContentToRoots(module, vDir);
        }
      }
    });
    if (exception[0] != null) throw exception[0];

    return vDir;
  }

  public static void removeAllRoots(final Module module, final Sdk jdk) {
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.clear();
        rootModel.setSdk(jdk);
        rootModel.commit();
      }
    }.execute().throwException();
  }

  public static void addSourceContentToRoots(Module module, @NotNull VirtualFile vDir) {
    addSourceContentToRoots(module, vDir, false);
  }

  public static void addSourceContentToRoots(final Module module, @NotNull final VirtualFile vDir, final boolean testSource) {
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        final ContentEntry contentEntry = rootModel.addContentEntry(vDir);
        contentEntry.addSourceFolder(vDir, testSource);
        rootModel.commit();
      }
    }.execute().throwException();
  }

  public static void addSourceRoot(Module module, final VirtualFile vDir) {
    addSourceRoot(module, vDir, false);
  }

  public static void addSourceRoot(final Module module, final VirtualFile vDir, final boolean isTestSource) {
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        final ContentEntry[] contentEntries = rootModel.getContentEntries();
        ContentEntry entry = ContainerUtil.find(contentEntries, new Condition<ContentEntry>() {
          @Override
          public boolean value(final ContentEntry object) {
            return VfsUtil.isAncestor(object.getFile(), vDir, false);
          }
        });
        if (entry == null) entry = rootModel.addContentEntry(vDir);
        entry.addSourceFolder(vDir, isTestSource);
        rootModel.commit();
      }
    }.execute().throwException();
  }

  public static ContentEntry addContentRoot(final Module module, final VirtualFile vDir) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        final ModifiableRootModel rootModel = rootManager.getModifiableModel();
        rootModel.addContentEntry(vDir);
        rootModel.commit();
      }
    }.execute().throwException();
    for (ContentEntry entry : rootManager.getContentEntries()) {
      if (entry.getFile() == vDir) {
        Assert.assertFalse(((ContentEntryImpl)entry).isDisposed());
        return entry;
      }
    }
    return null;
  }

  public static void addExcludedRoot(Module module, VirtualFile dir) {
    AccessToken token = WriteAction.start();
    try {
      final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
      boolean added = false;
      for (ContentEntry entry : model.getContentEntries()) {
        if (VfsUtil.isAncestor(entry.getFile(), dir, false)) {
          entry.addExcludeFolder(dir);
          added = true;
          break;
        }
      }
      if (!added) {
        throw new RuntimeException(dir + " is not under content roots: " + Arrays.toString(model.getContentRoots()));
      }
      model.commit();
    }
    finally {
      token.finish();
    }
  }

  public static void removeContentEntry(final Module module, final ContentEntry e) {
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        ModuleRootManager rootModel = ModuleRootManager.getInstance(module);
        ModifiableRootModel model = rootModel.getModifiableModel();
        model.removeContentEntry(e);
        model.commit();
      }
    }.execute().throwException();
  }

  public static void checkFileStructure(PsiFile file) throws IncorrectOperationException {
    String originalTree = DebugUtil.psiTreeToString(file, false);
    PsiFile dummyFile = PsiFileFactory.getInstance(file.getProject()).createFileFromText(file.getName(), file.getText());
    String reparsedTree = DebugUtil.psiTreeToString(dummyFile, false);
    Assert.assertEquals(reparsedTree, originalTree);
  }

  public static void addLibrary(final Module module, final String libName, final String libPath, final String... jarArr) {
    assert ModuleRootManager.getInstance(module).getContentRoots().length > 0 : "content roots must not be empty";
    new WriteCommandAction(module.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        addLibrary(module, model, libName, libPath, jarArr);
        model.commit();
      }
    }.execute().throwException();
  }

  public static void addLibrary(final Module module, final ModifiableRootModel model, final String libName, final String libPath, final String... jarArr) {
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        final LibraryTable libraryTable = ProjectLibraryTable.getInstance(module.getProject());
        final Library library = libraryTable.createLibrary(libName);
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (String jar : jarArr) {
          if (!libPath.endsWith("/") && !jar.startsWith("/")) {
            jar = "/" + jar;
          }
          final String path = libPath + jar;
          VirtualFile root;
          if (path.endsWith(".jar")) {
            root = JarFileSystem.getInstance().refreshAndFindFileByPath(path + "!/");
          } else {
            root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
          }
          assert root != null : "Library root folder not found: " + path + "!/";
          libraryModel.addRoot(root, OrderRootType.CLASSES);
        }
        libraryModel.commit();
        model.addLibraryEntry(library);
        final OrderEntry[] orderEntries = model.getOrderEntries();
        OrderEntry last = orderEntries[orderEntries.length - 1];
        for (int i = orderEntries.length - 2; i > -1; i--) {
          orderEntries[i + 1] = orderEntries[i];
        }
        orderEntries[0] = last;
        model.rearrangeOrderEntries(orderEntries);
      }
    }.execute().throwException();
  }

  public static void addLibrary(final Module module,
                                final String libName, final String libDir,
                                final String[] classRoots,
                                final String[] sourceRoots) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      public void run() {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        final String parentUrl = VirtualFileManager.constructUrl(JarFileSystem.PROTOCOL, libDir);
        final Library library = model.getModuleLibraryTable().createLibrary(libName);
        final Library.ModifiableModel libModifiableModel = library.getModifiableModel();
        for (String classRoot : classRoots) {
          libModifiableModel.addRoot(parentUrl + classRoot, OrderRootType.CLASSES);
        }
        for (String sourceRoot : sourceRoots) {
          libModifiableModel.addRoot(parentUrl + sourceRoot, OrderRootType.SOURCES);
        }
        libModifiableModel.commit();
        model.commit();
      }
    });
  }
}