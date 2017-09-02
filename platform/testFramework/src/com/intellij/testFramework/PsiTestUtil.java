/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.application.RunResult;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.projectRoots.SdkModificator;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.impl.ContentEntryImpl;
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.stubs.StubTextInconsistencyException;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.JpsElement;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;
import org.junit.Assert;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Consumer;

public class PsiTestUtil {
  public static VirtualFile createTestProjectStructure(Project project,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<File> filesToDelete) throws Exception {
    return createTestProjectStructure(project, module, rootPath, filesToDelete, true);
  }

  public static VirtualFile createTestProjectStructure(Project project, Module module, Collection<File> filesToDelete) throws IOException {
    return createTestProjectStructure(project, module, null, filesToDelete, true);
  }

  public static VirtualFile createTestProjectStructure(Project project,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    VirtualFile vDir = createTestProjectStructure(module, rootPath, filesToDelete, addProjectRoots);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    return vDir;
  }

  public static VirtualFile createTestProjectStructure(Module module,
                                                       String rootPath,
                                                       Collection<File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    return createTestProjectStructure("unitTest", module, rootPath, filesToDelete, addProjectRoots);
  }

  public static VirtualFile createTestProjectStructure(String tempName,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    File dir = FileUtil.createTempDirectory(tempName, null, false);
    filesToDelete.add(dir);

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assert vDir != null && vDir.isDirectory() : dir;
    PlatformTestCase.synchronizeTempDirVfs(vDir);

    EdtTestUtil.runInEdtAndWait(() -> {
      WriteAction.run(() -> {
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
      });
    });
    return vDir;
  }

  public static void removeAllRoots(@NotNull Module module, Sdk jdk) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      model.clear();
      model.setSdk(jdk);
    });
  }

  public static void addSourceContentToRoots(Module module, @NotNull VirtualFile vDir) {
    addSourceContentToRoots(module, vDir, false);
  }

  public static void addSourceContentToRoots(Module module, @NotNull VirtualFile vDir, boolean testSource) {
    ModuleRootModificationUtil.updateModel(module, model -> model.addContentEntry(vDir).addSourceFolder(vDir, testSource));
  }

  public static void addSourceRoot(Module module, VirtualFile vDir) {
    addSourceRoot(module, vDir, false);
  }

  public static void addSourceRoot(Module module, VirtualFile vDir, boolean isTestSource) {
    addSourceRoot(module, vDir, isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
  }

  public static <P extends JpsElement> void addSourceRoot(Module module, VirtualFile vDir, @NotNull JpsModuleSourceRootType<P> rootType) {
    addSourceRoot(module, vDir, rootType, rootType.createDefaultProperties());
  }

  public static <P extends JpsElement> void addSourceRoot(Module module,
                                                          VirtualFile vDir,
                                                          @NotNull JpsModuleSourceRootType<P> rootType,
                                                          P properties) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntry(model, vDir);
      if (entry == null) entry = model.addContentEntry(vDir);
      entry.addSourceFolder(vDir, rootType, properties);
    });
  }

  @Nullable
  private static ContentEntry findContentEntry(ModuleRootModel rootModel, VirtualFile file) {
    return ContainerUtil.find(rootModel.getContentEntries(), object -> {
      VirtualFile entryRoot = object.getFile();
      return entryRoot != null && VfsUtilCore.isAncestor(entryRoot, file, false);
    });
  }

  public static ContentEntry addContentRoot(Module module, VirtualFile vDir) {
    ModuleRootModificationUtil.updateModel(module, model -> model.addContentEntry(vDir));

    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      if (Comparing.equal(entry.getFile(), vDir)) {
        Assert.assertFalse(((ContentEntryImpl)entry).isDisposed());
        return entry;
      }
    }

    return null;
  }

  public static void addExcludedRoot(Module module, VirtualFile dir) {
    ModuleRootModificationUtil.updateModel(module, model -> ApplicationManager.getApplication().runReadAction(() -> {
      findContentEntryWithAssertion(model, dir).addExcludeFolder(dir);
    }));
  }

  @NotNull
  private static ContentEntry findContentEntryWithAssertion(ModifiableRootModel model, VirtualFile dir) {
    ContentEntry entry = findContentEntry(model, dir);
    if (entry == null) {
      throw new RuntimeException(dir + " is not under content roots: " + Arrays.toString(model.getContentRoots()));
    }
    return entry;
  }

  public static void removeContentEntry(Module module, VirtualFile contentRoot) {
    ModuleRootModificationUtil.updateModel(module, model -> model.removeContentEntry(findContentEntryWithAssertion(model, contentRoot)));
  }

  public static void removeSourceRoot(Module module, VirtualFile root) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntryWithAssertion(model, root);
      for (SourceFolder sourceFolder : entry.getSourceFolders()) {
        if (root.equals(sourceFolder.getFile())) {
          entry.removeSourceFolder(sourceFolder);
          break;
        }
      }
    });
  }

  public static void removeExcludedRoot(Module module, VirtualFile root) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntryWithAssertion(model, root);
      entry.removeExcludeFolder(root.getUrl());
    });
  }

  public static void checkFileStructure(PsiFile file) {
    compareFromAllRoots(file, f -> DebugUtil.psiTreeToString(f, false));
  }

  private static void compareFromAllRoots(PsiFile file, Function<PsiFile, String> fun) {
    PsiFile dummyFile = createDummyCopy(file);

    String psiTree = StringUtil.join(file.getViewProvider().getAllFiles(), fun, "\n");
    String reparsedTree = StringUtil.join(dummyFile.getViewProvider().getAllFiles(), fun, "\n");
    if (!psiTree.equals(reparsedTree)) {
      Assert.assertEquals("Re-created from text:\n" + reparsedTree, "PSI structure:\n" + psiTree);
    }
  }

  @NotNull
  private static PsiFile createDummyCopy(PsiFile file) {
    LightVirtualFile copy = new LightVirtualFile(file.getName(), file.getText());
    copy.setOriginalFile(file.getViewProvider().getVirtualFile());
    return Objects.requireNonNull(file.getManager().findFile(copy));
  }

  public static void checkPsiMatchesTextIgnoringNonCode(PsiFile file) {
    compareFromAllRoots(file, f -> DebugUtil.psiToStringIgnoringNonCode(f));
  }

  public static void addLibrary(Module module, String libPath) {
    File file = new File(libPath);
    String libName = file.getName();
    addLibrary(module, libName, file.getParent(), libName);
  }

  public static void addLibrary(Module module, String libName, String libPath, String... jarArr) {
    ModuleRootModificationUtil.updateModel(module, model -> addLibrary(module, model, libName, libPath, jarArr));
  }

  public static void addProjectLibrary(Module module, String libName, List<String> classesRootPaths) {
    List<VirtualFile> roots = ContainerUtil.map(classesRootPaths, path -> VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(path))));
    addProjectLibrary(module, libName, roots, Collections.emptyList());
  }

  public static void addProjectLibrary(Module module, String libName, VirtualFile... classesRoots) {
    addProjectLibrary(module, libName, Arrays.asList(classesRoots), Collections.emptyList());
  }

  public static Library addProjectLibrary(Module module, String libName, List<VirtualFile> classesRoots, List<VirtualFile> sourceRoots) {
    Ref<Library> result = Ref.create();
    ModuleRootModificationUtil.updateModel(module, model -> result.set(addProjectLibrary(module, model, libName, classesRoots, sourceRoots)));
    return result.get();
  }

  private static Library addProjectLibrary(Module module,
                                           ModifiableRootModel model,
                                           String libName,
                                           List<VirtualFile> classesRoots,
                                           List<VirtualFile> sourceRoots) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(module.getProject());
    RunResult<Library> result = new WriteAction<Library>() {
      @Override
      protected void run(@NotNull Result<Library> result) {
        Library library = libraryTable.createLibrary(libName);
        Library.ModifiableModel libraryModel = library.getModifiableModel();
        try {
          for (VirtualFile root : classesRoots) {
            libraryModel.addRoot(root, OrderRootType.CLASSES);
          }
          for (VirtualFile root : sourceRoots) {
            libraryModel.addRoot(root, OrderRootType.SOURCES);
          }
          libraryModel.commit();
        }
        catch (Throwable t) {
          //noinspection SSBasedInspection
          libraryModel.dispose();
          throw t;
        }

        model.addLibraryEntry(library);
        OrderEntry[] orderEntries = model.getOrderEntries();
        OrderEntry last = orderEntries[orderEntries.length - 1];
        System.arraycopy(orderEntries, 0, orderEntries, 1, orderEntries.length - 1);
        orderEntries[0] = last;
        model.rearrangeOrderEntries(orderEntries);
        result.setResult(library);
      }
    }.execute();
    result.throwException();
    return result.getResultObject();
  }

  public static void addLibrary(Module module,
                                ModifiableRootModel model,
                                String libName,
                                String libPath,
                                String... jarArr) {
    List<VirtualFile> classesRoots = new ArrayList<>();
    for (String jar : jarArr) {
      if (!libPath.endsWith("/") && !jar.startsWith("/")) {
        jar = "/" + jar;
      }
      String path = libPath + jar;
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
    addProjectLibrary(module, model, libName, classesRoots, Collections.emptyList());
  }

  public static void addLibrary(Module module,
                                String libName, String libDir,
                                String[] classRoots,
                                String[] sourceRoots) {
    String proto = (classRoots.length > 0 ? classRoots[0] : sourceRoots[0]).endsWith(".jar!/") ? JarFileSystem.PROTOCOL : LocalFileSystem.PROTOCOL;
    String parentUrl = VirtualFileManager.constructUrl(proto, libDir);
    List<String> classesUrls = new ArrayList<>();
    List<String> sourceUrls = new ArrayList<>();
    for (String classRoot : classRoots) {
      classesUrls.add(parentUrl + classRoot);
    }
    for (String sourceRoot : sourceRoots) {
      sourceUrls.add(parentUrl + sourceRoot);
    }
    ModuleRootModificationUtil.addModuleLibrary(module, libName, classesUrls, sourceUrls);
  }

  public static Module addModule(Project project, ModuleType type, String name, VirtualFile root) {
    return new WriteCommandAction<Module>(project) {
      @Override
      protected void run(@NotNull Result<Module> result) {
        String moduleName;
        ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
        try {
          moduleName = moduleModel.newModule(root.getPath() + "/" + name + ".iml", type.getId()).getName();
          moduleModel.commit();
        }
        catch (Throwable t) {
          moduleModel.dispose();
          throw t;
        }

        Module dep = ModuleManager.getInstance(project).findModuleByName(moduleName);
        assert dep != null : moduleName;

        ModifiableRootModel model = ModuleRootManager.getInstance(dep).getModifiableModel();
        try {
          model.addContentEntry(root).addSourceFolder(root, false);
          model.commit();
        }
        catch (Throwable t) {
          model.dispose();
          throw t;
        }

        result.setResult(dep);
      }
    }.execute().getResultObject();
  }

  public static void setCompilerOutputPath(Module module, String url, boolean forTests) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      CompilerModuleExtension extension = model.getModuleExtension(CompilerModuleExtension.class);
      extension.inheritCompilerOutputPath(false);
      if (forTests) {
        extension.setCompilerOutputPathForTests(url);
      }
      else {
        extension.setCompilerOutputPath(url);
      }
    });
  }

  public static void setExcludeCompileOutput(Module module, boolean exclude) {
    ModuleRootModificationUtil.updateModel(module, model -> model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(exclude));
  }

  public static void setJavadocUrls(Module module, String... urls) {
    ModuleRootModificationUtil.updateModel(module, model -> model.getModuleExtension(JavaModuleExternalPaths.class).setJavadocUrls(urls));
  }

  @NotNull
  @Contract(pure=true)
  public static Sdk addJdkAnnotations(@NotNull Sdk sdk) {
    String path = FileUtil.toSystemIndependentName(PlatformTestUtil.getCommunityPath()) + "/java/jdkAnnotations";
    VirtualFile root = LocalFileSystem.getInstance().findFileByPath(path);
    return addRootsToJdk(sdk, AnnotationOrderRootType.getInstance(), root);
  }

  @NotNull
  @Contract(pure=true)
  public static Sdk addRootsToJdk(@NotNull Sdk sdk,
                                  @NotNull OrderRootType rootType,
                                  @NotNull VirtualFile... roots) {
    Sdk clone;
    try {
      clone = (Sdk)sdk.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    SdkModificator sdkModificator = clone.getSdkModificator();
    for (VirtualFile root : roots) {
      sdkModificator.addRoot(root, rootType);
    }
    sdkModificator.commitChanges();
    return clone;
  }

  public static void checkStubsMatchText(@NotNull PsiFile file) {
    try {
      StubTextInconsistencyException.checkStubTextConsistency(file);
    }
    catch (StubTextInconsistencyException e) {
      Assert.assertEquals("Re-created from text:\n" + e.getStubsFromText(), "Stubs from PSI structure:\n" + e.getStubsFromPsi());
      throw e;
    }
  }

  public static void checkPsiStructureWithCommit(@NotNull PsiFile psiFile, Consumer<PsiFile> checker) {
    checker.accept(psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    Project project = psiFile.getProject();
    if (document != null && PsiDocumentManager.getInstance(project).isUncommited(document)) {
      PsiDocumentManager.getInstance(project).commitDocument(document);
      checker.accept(psiFile);
    }
  }
}