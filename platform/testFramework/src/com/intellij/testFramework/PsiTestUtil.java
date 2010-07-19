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

import com.intellij.openapi.application.Result;
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
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiFileFactory;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.containers.ContainerUtil;
import junit.framework.Assert;
import org.jetbrains.annotations.NonNls;

import java.io.File;
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

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));

    if (rootPath != null) {
      VirtualFile vDir1 = LocalFileSystem.getInstance().findFileByPath(rootPath.replace(File.separatorChar, '/'));
      if (vDir1 == null) {
        throw new Exception(rootPath + " not found");
      }
      VfsUtil.copyDirectory(null, vDir1, vDir, null);
    }

    if (addProjectRoots) {
      addSourceContentToRoots(module, vDir);
    }
    return vDir;
  }

  public static void removeAllRoots(Module module, final Sdk jdk) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    rootModel.clear();
    rootModel.setSdk(jdk);
    rootModel.commit();
  }

  public static void addSourceContentToRoots(Module module, VirtualFile vDir) {
    addSourceContentToRoots(module, vDir, false);
  }

  public static void addSourceContentToRoots(Module module, VirtualFile vDir, final boolean testSource) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    final ContentEntry contentEntry = rootModel.addContentEntry(vDir);
    contentEntry.addSourceFolder(vDir, testSource);
    rootModel.commit();
  }

  public static void addSourceRoot(Module module, final VirtualFile vDir) {
    addSourceRoot(module, vDir, false);
  }

  public static void addSourceRoot(final Module module, final VirtualFile vDir, final boolean isTestSource) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    final ContentEntry[] contentEntries = rootModel.getContentEntries();
    ContentEntry entry = ContainerUtil.find(contentEntries, new Condition<ContentEntry>() {
      public boolean value(final ContentEntry object) {
        return VfsUtil.isAncestor(object.getFile(), vDir, false);
      }
    });
    if (entry == null) entry = rootModel.addContentEntry(vDir);
    entry.addSourceFolder(vDir, isTestSource);
    rootModel.commit();
  }

  public static ContentEntry addContentRoot(Module module, VirtualFile vDir) {
    final ModuleRootManager rootManager = ModuleRootManager.getInstance(module);
    final ModifiableRootModel rootModel = rootManager.getModifiableModel();
    rootModel.addContentEntry(vDir);
    rootModel.commit();
    for (ContentEntry entry : rootManager.getContentEntries()) {
      if (entry.getFile() == vDir) {
        Assert.assertFalse(((ContentEntryImpl)entry).isDisposed());
        return entry;
      }
    }
    return null;
  }

  public static void removeContentEntry(Module m, ContentEntry e) {
    ModuleRootManager rootModel = ModuleRootManager.getInstance(m);
    ModifiableRootModel model = rootModel.getModifiableModel();
    model.removeContentEntry(e);
    model.commit();
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
      protected void run(Result result) throws Throwable {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        addLibrary(module, model, libName, libPath, jarArr);
        model.commit();
      }
    }.execute();
  }

  public static void addLibrary(Module module, ModifiableRootModel model, String libName, String libPath, String... jarArr) {
    final LibraryTable libraryTable = ProjectLibraryTable.getInstance(module.getProject());
    final Library library = libraryTable.createLibrary(libName);
    final Library.ModifiableModel libraryModel = library.getModifiableModel();
    for (String jar : jarArr) {
      if (!libPath.endsWith("/") && !jar.startsWith("/")) {
        jar = "/" + jar;
      }
      final String path = libPath + jar;
      final VirtualFile root = JarFileSystem.getInstance().refreshAndFindFileByPath(path + "!/");
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
}