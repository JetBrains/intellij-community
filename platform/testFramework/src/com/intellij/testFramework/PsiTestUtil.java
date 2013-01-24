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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.Result;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
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
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@NonNls
public class PsiTestUtil {
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
    return createTestProjectStructure("unitTest", module, rootPath, filesToDelete, addProjectRoots);
  }

  public static VirtualFile createTestProjectStructure(String tempName,
                                                       final Module module,
                                                       final String rootPath,
                                                       final Collection<File> filesToDelete,
                                                       final boolean addProjectRoots) throws Exception {
    File dir = FileUtil.createTempDirectory(tempName, null, false);
    filesToDelete.add(dir);

    final VirtualFile vDir =
      LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assert vDir.isDirectory() : vDir;

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
        ContentEntry entry = findContentEntry(rootModel, vDir);
        if (entry == null) entry = rootModel.addContentEntry(vDir);
        entry.addSourceFolder(vDir, isTestSource);
        rootModel.commit();
      }
    }.execute().throwException();
  }

  @Nullable
  private static ContentEntry findContentEntry(ModuleRootModel rootModel, final VirtualFile file) {
    return ContainerUtil.find(rootModel.getContentEntries(), new Condition<ContentEntry>() {
      @Override
      public boolean value(final ContentEntry object) {
        VirtualFile entryRoot = object.getFile();
        return entryRoot != null && VfsUtilCore.isAncestor(entryRoot, file, false);
      }
    });
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
      if (Comparing.equal(entry.getFile(), vDir)) {
        Assert.assertFalse(((ContentEntryImpl)entry).isDisposed());
        return entry;
      }
    }
    return null;
  }

  public static void addExcludedRoot(Module module, VirtualFile dir) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    findContentEntryWithAssertion(model, dir).addExcludeFolder(dir);
    commitModel(model);
  }

  @NotNull
  private static ContentEntry findContentEntryWithAssertion(ModifiableRootModel model, VirtualFile dir) {
    ContentEntry entry = findContentEntry(model, dir);
    if (entry == null) {
      throw new RuntimeException(dir + " is not under content roots: " + Arrays.toString(model.getContentRoots()));
    }
    return entry;
  }

  public static void removeContentEntry(final Module module, final ContentEntry e) {
    ModuleRootManager rootModel = ModuleRootManager.getInstance(module);
    final ModifiableRootModel model = rootModel.getModifiableModel();
    model.removeContentEntry(e);
    commitModel(model);
  }

  public static void removeSourceRoot(Module module, VirtualFile root) {
    final ModifiableRootModel rootModel = ModuleRootManager.getInstance(module).getModifiableModel();
    ContentEntry entry = findContentEntryWithAssertion(rootModel, root);
    for (SourceFolder sourceFolder : entry.getSourceFolders()) {
      if (root.equals(sourceFolder.getFile())) {
        entry.removeSourceFolder(sourceFolder);
        break;
      }
    }
    commitModel(rootModel);
  }

  public static void removeExcludedRoot(Module module, VirtualFile root) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    ContentEntry entry = findContentEntryWithAssertion(model, root);
    final ExcludeFolder[] excludeFolders = entry.getExcludeFolders();
    for (ExcludeFolder excludeFolder : excludeFolders) {
      if (root.equals(excludeFolder.getFile())) {
        entry.removeExcludeFolder(excludeFolder);
      }
    }
    commitModel(model);
  }

  private static void commitModel(final ModifiableRootModel model) {
    new WriteCommandAction.Simple(model.getProject()) {
      @Override
      protected void run() throws Throwable {
        model.commit();
      }
    }.execute().throwException();
  }

  public static void checkFileStructure(PsiFile file) throws IncorrectOperationException {
    String originalTree = DebugUtil.psiTreeToString(file, false);
    PsiFile dummyFile = PsiFileFactory.getInstance(file.getProject()).createFileFromText(file.getName(), file.getFileType(), file.getText());
    String reparsedTree = DebugUtil.psiTreeToString(dummyFile, false);
    Assert.assertEquals(reparsedTree, originalTree);
  }

  public static void addLibrary(final Module module, final String libName, final String libPath, final String... jarArr) {
    new WriteCommandAction(module.getProject()) {
      @Override
      protected void run(Result result) throws Throwable {
        final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
        addLibrary(module, model, libName, libPath, jarArr);
        model.commit();
      }
    }.execute().throwException();
  }

  public static void addProjectLibrary(Module module, String libName, VirtualFile... classesRoots) {
    final ModifiableRootModel modifiableModel = ModuleRootManager.getInstance(module).getModifiableModel();
    addProjectLibrary(module, modifiableModel, libName, classesRoots);
    commitModel(modifiableModel);
  }

  private static void addProjectLibrary(final Module module,
                                        final ModifiableRootModel model,
                                        final String libName,
                                        final VirtualFile... classesRoots) {
    new WriteCommandAction.Simple(module.getProject()) {
      @Override
      protected void run() throws Throwable {
        final LibraryTable libraryTable = ProjectLibraryTable.getInstance(module.getProject());
        final Library library = libraryTable.createLibrary(libName);
        final Library.ModifiableModel libraryModel = library.getModifiableModel();
        for (VirtualFile root : classesRoots) {
          libraryModel.addRoot(root, OrderRootType.CLASSES);
        }
        libraryModel.commit();
        model.addLibraryEntry(library);
        final OrderEntry[] orderEntries = model.getOrderEntries();
        OrderEntry last = orderEntries[orderEntries.length - 1];
        System.arraycopy(orderEntries, 0, orderEntries, 1, orderEntries.length - 1);
        orderEntries[0] = last;
        model.rearrangeOrderEntries(orderEntries);
      }
    }.execute().throwException();
  }

  public static void addLibrary(final Module module,
                                final ModifiableRootModel model,
                                final String libName,
                                final String libPath,
                                final String... jarArr) {
    List<VirtualFile> classesRoots = new ArrayList<VirtualFile>();
    for (String jar : jarArr) {
      if (!libPath.endsWith("/") && !jar.startsWith("/")) {
        jar = "/" + jar;
      }
      final String path = libPath + jar;
      VirtualFile root;
      if (path.endsWith(".jar")) {
        root = JarFileSystem.getInstance().refreshAndFindFileByPath(path + "!/");
      }
      else {
        root = LocalFileSystem.getInstance().refreshAndFindFileByPath(path);
      }
      assert root != null : "Library root folder not found: " + path + "!/";
      classesRoots.add(root);
    }
    addProjectLibrary(module, model, libName, VfsUtil.toVirtualFileArray(classesRoots));
  }

  public static void addLibrary(final Module module,
                                final String libName, final String libDir,
                                final String[] classRoots,
                                final String[] sourceRoots) {
    final String parentUrl =
      VirtualFileManager.constructUrl(classRoots[0].endsWith(".jar!/") ? JarFileSystem.PROTOCOL : LocalFileSystem.PROTOCOL, libDir);
    List<String> classesUrls = new ArrayList<String>();
    List<String> sourceUrls = new ArrayList<String>();
    for (String classRoot : classRoots) {
      classesUrls.add(parentUrl + classRoot);
    }
    for (String sourceRoot : sourceRoots) {
      sourceUrls.add(parentUrl + sourceRoot);
    }
    ModuleRootModificationUtil.addModuleLibrary(module, libName, classesUrls, sourceUrls);
  }

  public static Module addModule(final Project project, final ModuleType type, final String name, final VirtualFile root) {
    return new WriteCommandAction<Module>(project) {
      @Override
      protected void run(Result<Module> result) throws Throwable {
        final ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
        String moduleName = moduleModel.newModule(root.getPath() + "/" + name + ".iml", type.getId()).getName();
        moduleModel.commit();

        final Module dep = ModuleManager.getInstance(project).findModuleByName(moduleName);
        final ModifiableRootModel model = ModuleRootManager.getInstance(dep).getModifiableModel();
        final ContentEntry entry = model.addContentEntry(root);
        entry.addSourceFolder(root, false);

        model.commit();
        result.setResult(dep);
      }
    }.execute().getResultObject();
  }

  public static void setCompilerOutputPath(Module module, String url, boolean forTests) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final CompilerModuleExtension extension = model.getModuleExtension(CompilerModuleExtension.class);
    extension.inheritCompilerOutputPath(false);
    if (forTests) {
      extension.setCompilerOutputPathForTests(url);
    }
    else {
      extension.setCompilerOutputPath(url);
    }
    commitModel(model);
  }

  public static void setExcludeCompileOutput(Module module, boolean exclude) {
    final ModifiableRootModel model = ModuleRootManager.getInstance(module).getModifiableModel();
    final CompilerModuleExtension extension = model.getModuleExtension(CompilerModuleExtension.class);
    extension.setExcludeOutput(exclude);
    commitModel(model);
  }
}