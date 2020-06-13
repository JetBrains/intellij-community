// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testFramework;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
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
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.TextRange;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.psi.*;
import com.intellij.psi.impl.DebugUtil;
import com.intellij.psi.impl.source.PsiFileImpl;
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

public final class PsiTestUtil {
  @NotNull
  public static VirtualFile createTestProjectStructure(@NotNull Project project,
                                                       @NotNull Module module,
                                                       String rootPath,
                                                       @NotNull Collection<? super File> filesToDelete) throws Exception {
    return createTestProjectStructure(project, module, rootPath, filesToDelete, true);
  }

  @NotNull
  public static VirtualFile createTestProjectStructure(@NotNull Project project, @NotNull Module module, @NotNull Collection<? super File> filesToDelete) throws IOException {
    return createTestProjectStructure(project, module, null, filesToDelete, true);
  }

  @NotNull
  public static VirtualFile createTestProjectStructure(@NotNull Project project,
                                                       @Nullable Module module,
                                                       String rootPath,
                                                       @NotNull Collection<? super File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    VirtualFile vDir = createTestProjectStructure("unitTest", module, rootPath, filesToDelete, addProjectRoots);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    return vDir;
  }

  @NotNull
  public static VirtualFile createTestProjectStructure(@NotNull String tempName,
                                                       @Nullable Module module,
                                                       String rootPath,
                                                       @NotNull Collection<? super File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    File dir = FileUtil.createTempDirectory(tempName, null, false);
    filesToDelete.add(dir);

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assert vDir != null && vDir.isDirectory() : dir;
    HeavyPlatformTestCase.synchronizeTempDirVfs(vDir);

    EdtTestUtil.runInEdtAndWait(() -> WriteAction.run(() -> {
      if (rootPath != null) {
        VirtualFile vDir1 =
          LocalFileSystem.getInstance().findFileByPath(rootPath.replace(File.separatorChar, '/'));
        if (vDir1 == null) {
          throw new Exception(rootPath + " not found");
        }
        VfsUtil.copyDirectory(null, vDir1, vDir, null);
      }

      if (addProjectRoots) {
        addSourceContentToRoots(module, vDir);
      }
    }));
    return vDir;
  }

  public static void removeAllRoots(@NotNull Module module, Sdk jdk) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      model.clear();
      model.setSdk(jdk);
    });
  }

  @NotNull
  public static SourceFolder addSourceContentToRoots(@NotNull Module module, @NotNull VirtualFile vDir) {
    return addSourceContentToRoots(module, vDir, false);
  }

  @NotNull
  public static SourceFolder addSourceContentToRoots(@NotNull Module module, @NotNull VirtualFile vDir, boolean testSource) {
    Ref<SourceFolder> result = Ref.create();
    ModuleRootModificationUtil.updateModel(module, model -> result.set(model.addContentEntry(vDir).addSourceFolder(vDir, testSource)));
    return result.get();
  }

  @NotNull
  public static SourceFolder addSourceRoot(@NotNull Module module, @NotNull VirtualFile vDir) {
    return addSourceRoot(module, vDir, false);
  }

  @NotNull
  public static SourceFolder addSourceRoot(@NotNull Module module, @NotNull VirtualFile vDir, boolean isTestSource) {
    return addSourceRoot(module, vDir, isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
  }

  @NotNull
  public static <P extends JpsElement> SourceFolder addSourceRoot(@NotNull Module module, @NotNull VirtualFile vDir, @NotNull JpsModuleSourceRootType<P> rootType) {
    return addSourceRoot(module, vDir, rootType, rootType.createDefaultProperties());
  }

  @NotNull
  public static <P extends JpsElement> SourceFolder addSourceRoot(@NotNull Module module,
                                                                  @NotNull VirtualFile vDir,
                                                                  @NotNull JpsModuleSourceRootType<P> rootType,
                                                                  @NotNull P properties) {
    Ref<SourceFolder> result = Ref.create();
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntry(model, vDir);
      if (entry == null) entry = model.addContentEntry(vDir);
      result.set(entry.addSourceFolder(vDir, rootType, properties));
    });
    return result.get();
  }

  @Nullable
  private static ContentEntry findContentEntry(@NotNull ModuleRootModel rootModel, @NotNull VirtualFile file) {
    return ContainerUtil.find(rootModel.getContentEntries(), object -> {
      VirtualFile entryRoot = object.getFile();
      return entryRoot != null && VfsUtilCore.isAncestor(entryRoot, file, false);
    });
  }

  public static ContentEntry addContentRoot(@NotNull Module module, @NotNull VirtualFile vDir) {
    ModuleRootModificationUtil.updateModel(module, model -> model.addContentEntry(vDir));

    for (ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
      if (Comparing.equal(entry.getFile(), vDir)) {
        if (entry instanceof ContentEntryImpl) {
          Assert.assertFalse(((ContentEntryImpl)entry).isDisposed());
        }

        return entry;
      }
    }

    return null;
  }

  public static void addExcludedRoot(@NotNull Module module, @NotNull VirtualFile dir) {
    ModuleRootModificationUtil.updateModel(module, model -> ApplicationManager.getApplication().runReadAction(() -> {
      findContentEntryWithAssertion(model, dir).addExcludeFolder(dir);
    }));
  }

  @NotNull
  private static ContentEntry findContentEntryWithAssertion(@NotNull ModifiableRootModel model, @NotNull VirtualFile dir) {
    return assertEntryFound(model, dir, findContentEntry(model, dir));
  }

  @NotNull
  private static ContentEntry assertEntryFound(@NotNull ModifiableRootModel model,
                                               @NotNull VirtualFile dir, ContentEntry entry) {
    if (entry == null) {
      throw new RuntimeException(dir + " is not under content roots: " + Arrays.toString(model.getContentRoots()));
    }
    return entry;
  }

  public static void removeContentEntry(@NotNull Module module, @NotNull VirtualFile contentRoot) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = ContainerUtil.find(model.getContentEntries(), object -> contentRoot.equals(object.getFile()));
      model.removeContentEntry(assertEntryFound(model, contentRoot, entry));
    });
  }

  public static void removeSourceRoot(@NotNull Module module, @NotNull VirtualFile root) {
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

  public static void removeExcludedRoot(@NotNull Module module, @NotNull VirtualFile root) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntryWithAssertion(model, root);
      entry.removeExcludeFolder(root.getUrl());
    });
  }

  public static void checkErrorElements(@NotNull PsiElement element) {
    StringBuilder err = null;
    int s = 0;
    String text = element.getText();
    for (PsiErrorElement error : SyntaxTraverser.psiTraverser().withRoot(element).filter(PsiErrorElement.class)) {
      if (err == null) err = new StringBuilder();
      TextRange r = error.getTextRange();
      if (r.getStartOffset() < s) continue;
      err.append(text, s, r.getStartOffset()).append("<error desc=\"");
      err.append(error.getErrorDescription()).append("\">");
      err.append(error.getText()).append("</error>");
      s = r.getEndOffset();
    }
    if (err == null) return;
    err.append(text, s, text.length());
    UsefulTestCase.assertSameLines(text, err.toString());
  }

  public static void checkFileStructure(@NotNull PsiFile file) {
    compareFromAllRoots(file, f -> DebugUtil.psiTreeToString(f, false));
  }

  private static void compareFromAllRoots(@NotNull PsiFile file, @NotNull Function<? super PsiFile, String> fun) {
    PsiFile dummyFile = createDummyCopy(file);

    String psiTree = StringUtil.join(file.getViewProvider().getAllFiles(), fun, "\n");
    String reparsedTree = StringUtil.join(dummyFile.getViewProvider().getAllFiles(), fun, "\n");
    assertPsiTextTreeConsistency(psiTree, reparsedTree);
  }

  private static void assertPsiTextTreeConsistency(@NotNull String psiTree, @NotNull String reparsedTree) {
    if (!psiTree.equals(reparsedTree)) {
      String[] psiLines = StringUtil.splitByLinesDontTrim(psiTree);
      String[] reparsedLines = StringUtil.splitByLinesDontTrim(reparsedTree);
      for (int i = 0; ; i++) {
        if (i >= psiLines.length || i >= reparsedLines.length || !psiLines[i].equals(reparsedLines[i])) {
          psiLines[Math.min(i, psiLines.length - 1)] += "   // in PSI structure";
          reparsedLines[Math.min(i, reparsedLines.length - 1)] += "   // re-created from text";
          break;
        }
      }
      psiTree = StringUtil.join(psiLines, "\n");
      reparsedTree = StringUtil.join(reparsedLines, "\n");
      Assert.assertEquals(reparsedTree, psiTree);
    }
  }

  @NotNull
  private static PsiFile createDummyCopy(@NotNull PsiFile file) {
    LightVirtualFile copy = new LightVirtualFile(file.getName(), file.getText());
    copy.setOriginalFile(file.getViewProvider().getVirtualFile());
    PsiFile dummyCopy = Objects.requireNonNull(file.getManager().findFile(copy));
    if (dummyCopy instanceof PsiFileImpl) {
      ((PsiFileImpl)dummyCopy).setOriginalFile(file);
    }
    return dummyCopy;
  }

  public static void checkPsiMatchesTextIgnoringNonCode(@NotNull PsiFile file) {
    compareFromAllRoots(file, f -> DebugUtil.psiToStringIgnoringNonCode(f));
  }

  /**
   * @deprecated to attract attention and motivate to fix tests which fail these checks
   */
  @Deprecated
  public static void disablePsiTextConsistencyChecks(@NotNull Disposable parentDisposable) {
    Registry.get("ide.check.structural.psi.text.consistency.in.tests").setValue(false, parentDisposable);
  }

  /**
   * Creates a builder for new library for the test project. After all the roots are added,
   * an {@code addTo} method must be called to actually create a library
   * @param name a name for the library
   * @return new {@link LibraryBuilder}.
   */
  @NotNull
  public static LibraryBuilder newLibrary(String name) {
    return new LibraryBuilder(name);
  }

  /**
   * Add a module-level library. If you already have a {@link ModifiableRootModel} (e.g. inside {@link LightProjectDescriptor#configureModule}),
   * use {@link #addLibrary(ModifiableRootModel, String, String, String...)}.
   * @param module where to add a module library
   * @param libPath the path of a single class root (jar or directory) of the created library
   */
  public static void addLibrary(@NotNull Module module, @NotNull String libPath) {
    File file = new File(libPath);
    String libName = file.getName();
    addLibrary(module, libName, file.getParent(), libName);
  }

  /**
   * Add a module-level library. If you already have a {@link ModifiableRootModel} (e.g. inside {@link LightProjectDescriptor#configureModule}),
   * use {@link #addLibrary(ModifiableRootModel, String, String, String...)}.
   * @param module where to add a module library
   * @param libName the name of the created library
   * @param libPath the path of a directory
   * @param jarArr the names of jars or subdirectories inside {@code libPath} that will become class roots
   */
  public static void addLibrary(@NotNull Module module, String libName, @NotNull String libPath, String @NotNull ... jarArr) {
    ModuleRootModificationUtil.updateModel(module, model -> addLibrary(model, libName, libPath, jarArr));
  }

  /**
   * Add a module-level library. Same as {@link #addLibrary(Module, String, String, String...)}, but the library will be removed when the {@code parent} disposable is disposed.
   */
  public static void addLibrary(@NotNull Disposable parent, @NotNull Module module, String libName, @NotNull String libPath, String @NotNull ... jarArr) {
    Ref<Library> ref = new Ref<>();
    ModuleRootModificationUtil.updateModel(module, model -> ref.set(addLibrary(model, libName, libPath, jarArr)));
    Disposer.register(parent, () -> {
      Library library = ref.get();
      ModuleRootModificationUtil.updateModel(module, model -> {
        LibraryOrderEntry entry = model.findLibraryOrderEntry(library);
        if (entry != null) {
          model.removeOrderEntry(entry);
        }
      });
      WriteCommandAction.runWriteCommandAction(null, ()-> {
        LibraryTable table = LibraryTablesRegistrar.getInstance().getLibraryTable(module.getProject());
        LibraryTable.ModifiableModel model = table.getModifiableModel();
        model.removeLibrary(library);
        model.commit();
      });
    });
  }

  /**
   * Add a project-level library and make the given module depend on it.
   * If you already have a {@link ModifiableRootModel} (e.g. inside {@link LightProjectDescriptor#configureModule}),
   * use {@link #addProjectLibrary(ModifiableRootModel, String, List)}.
   */
  public static void addProjectLibrary(@NotNull Module module, String libName, @NotNull List<String> classesRootPaths) {
    List<VirtualFile> roots = getLibraryRoots(classesRootPaths);
    addProjectLibrary(module, libName, roots, Collections.emptyList());
  }

  @NotNull
  private static List<VirtualFile> getLibraryRoots(@NotNull List<String> classesRootPaths) {
    return ContainerUtil.map(classesRootPaths, path -> VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(path))));
  }

  /**
   * Add a project-level library and make the given module depend on it.
   */
  public static void addProjectLibrary(@NotNull ModifiableRootModel model, String libName, @NotNull List<String> classesRootPaths) {
    List<VirtualFile> roots = getLibraryRoots(classesRootPaths);
    addProjectLibrary(model, libName, roots, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  /**
   * Add a project-level library and make the given module depend on it.
   * If you already have a {@link ModifiableRootModel} (e.g. inside {@link LightProjectDescriptor#configureModule}),
   * use {@link #addProjectLibrary(ModifiableRootModel, String, List)}.
   */
  public static void addProjectLibrary(@NotNull Module module, String libName, VirtualFile @NotNull ... classesRoots) {
    addProjectLibrary(module, libName, Arrays.asList(classesRoots), Collections.emptyList());
  }

  /**
   * Add a project-level library and make the given module depend on it.
   */
  @NotNull
  public static Library addProjectLibrary(@NotNull Module module, String libName, @NotNull List<? extends VirtualFile> classesRoots, @NotNull List<? extends VirtualFile> sourceRoots) {
    Ref<Library> result = Ref.create();
    ModuleRootModificationUtil.updateModel(
      module, model -> result.set(addProjectLibrary(model, libName, classesRoots, sourceRoots, Collections.emptyList(), Collections.emptyList())));
    return result.get();
  }

  @NotNull
  private static Library addProjectLibrary(@NotNull ModifiableRootModel model,
                                           String libName,
                                           @NotNull List<? extends VirtualFile> classesRoots,
                                           @NotNull List<? extends VirtualFile> sourceRoots,
                                           @NotNull List<? extends VirtualFile> javaDocs,
                                           @NotNull List<? extends VirtualFile> externalAnnotationsRoots) {
    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(model.getProject());
    return WriteAction.computeAndWait(() -> {
      Library library = libraryTable.createLibrary(libName);
      Library.ModifiableModel libraryModel = library.getModifiableModel();
      try {
        for (VirtualFile root : classesRoots) {
          libraryModel.addRoot(root, OrderRootType.CLASSES);
        }
        for (VirtualFile root : sourceRoots) {
          libraryModel.addRoot(root, OrderRootType.SOURCES);
        }
        for (VirtualFile root : javaDocs) {
          libraryModel.addRoot(root, JavadocOrderRootType.getInstance());
        }
        for (VirtualFile root : externalAnnotationsRoots) {
          libraryModel.addRoot(root, AnnotationOrderRootType.getInstance());
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
      return library;
    });
  }

  /**
   * Add a module-level library.
   * @param model a module's modifiable root model to add a library to
   * @param libName the name of the created library
   * @param libPath the path of a directory
   * @param jarArr the names of jars or subdirectories inside {@code libPath} that will become class roots
   * @return
   */
  @NotNull
  public static Library addLibrary(@NotNull ModifiableRootModel model,
                                   String libName,
                                   @NotNull String libPath,
                                   String @NotNull ... jarArr) {
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

    LibraryTable libraryTable = LibraryTablesRegistrar.getInstance().getLibraryTable(model.getProject());
    if (libraryTable.getLibraryByName(libName) != null) {
      for (int index = 0; index < 100000; index++) {
        String candidate = libName + "-" + index;
        if (libraryTable.getLibraryByName(candidate) == null) {
          libName = candidate;
          break;
        }
      }
    }

    return addProjectLibrary(model, libName, classesRoots, Collections.emptyList(), Collections.emptyList(), Collections.emptyList());
  }

  /**
   * Add a module-level library.
   * @param module where to add the library
   * @param libName the name of the created library
   * @param libDir the path of a directory
   * @param classRoots the names of jars or subdirectories relative to {@code libDir} that will become class roots
   * @param sourceRoots the names of jars or subdirectories relative to {@code libDir} that will become source roots
   * @return
   */
  public static void addLibrary(@NotNull Module module,
                                String libName,
                                @NotNull String libDir,
                                String @NotNull [] classRoots,
                                String @NotNull [] sourceRoots) {
    String proto = (classRoots.length > 0 ? classRoots[0] : sourceRoots[0]).endsWith(".jar!/") ? JarFileSystem.PROTOCOL : LocalFileSystem.PROTOCOL;
    String parentUrl = VirtualFileManager.constructUrl(proto, libDir);
    List<String> classesUrls = new ArrayList<>();
    for (String classRoot : classRoots) {
      classesUrls.add(parentUrl + classRoot);
    }
    List<String> sourceUrls = new ArrayList<>();
    for (String sourceRoot : sourceRoots) {
      sourceUrls.add(parentUrl + sourceRoot);
    }
    ModuleRootModificationUtil.addModuleLibrary(module, libName, classesUrls, sourceUrls);
  }

  @NotNull
  public static Module addModule(@NotNull Project project, @NotNull ModuleType type, @NotNull String name, @NotNull VirtualFile root) {
    return WriteCommandAction.writeCommandAction(project).compute(() -> {
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
      return dep;
    });
  }

  public static void setCompilerOutputPath(@NotNull Module module, @NotNull String url, boolean forTests) {
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

  public static void setExcludeCompileOutput(@NotNull Module module, boolean exclude) {
    ModuleRootModificationUtil.updateModel(module, model -> model.getModuleExtension(CompilerModuleExtension.class).setExcludeOutput(exclude));
  }

  public static void setJavadocUrls(@NotNull Module module, String @NotNull ... urls) {
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
                                  VirtualFile @NotNull ... roots) {
    return modifyJdkRoots(sdk, sdkModificator -> {
      for (VirtualFile root : roots) {
        sdkModificator.setName(sdkModificator.getName() + "+" + root.getPath());
        sdkModificator.addRoot(root, rootType);
      }
    });
  }

  @NotNull
  @Contract(pure=true)
  public static Sdk modifyJdkRoots(@NotNull Sdk sdk, Consumer<? super SdkModificator> modifier) {
    Sdk clone;
    try {
      clone = (Sdk)sdk.clone();
    }
    catch (CloneNotSupportedException e) {
      throw new RuntimeException(e);
    }
    SdkModificator sdkModificator = clone.getSdkModificator();
    modifier.accept(sdkModificator);
    sdkModificator.commitChanges();
    return clone;
  }

  public static void checkStubsMatchText(@NotNull PsiFile file) {
    try {
      StubTextInconsistencyException.checkStubTextConsistency(file);
    }
    catch (StubTextInconsistencyException e) {
      compareStubTexts(e);
    }
  }

  public static void compareStubTexts(@NotNull StubTextInconsistencyException e) {
    assertPsiTextTreeConsistency(e.getStubsFromPsi(), e.getStubsFromText());
    throw e;
  }

  public static void checkPsiStructureWithCommit(@NotNull PsiFile psiFile, @NotNull Consumer<? super PsiFile> checker) {
    checker.accept(psiFile);
    Document document = psiFile.getViewProvider().getDocument();
    PsiDocumentManager manager = PsiDocumentManager.getInstance(psiFile.getProject());
    if (document != null && manager.isUncommited(document)) {
      manager.commitDocument(document);
      checker.accept(manager.getPsiFile(document));
    }
  }

  public static class LibraryBuilder {
    private final String myName;
    private final List<VirtualFile> myClassesRoots = new ArrayList<>();
    private final List<VirtualFile> mySourceRoots = new ArrayList<>();
    private final List<VirtualFile> myJavaDocRoots = new ArrayList<>();
    private final List<VirtualFile> myExternalAnnotationsRoots = new ArrayList<>();

    private LibraryBuilder(String name) {
      myName = name;
    }

    /**
     * Add a classes root for the future library.
     * @param root root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder classesRoot(@NotNull VirtualFile root) {
      myClassesRoots.add(root);
      return this;
    }

    /**
     * Add a classes root for the future library.
     * @param rootPath root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder classesRoot(@NotNull String rootPath) {
      myClassesRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Add a source root for the future library.
     * @param root root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder sourceRoot(@NotNull VirtualFile root) {
      mySourceRoots.add(root);
      return this;
    }

    /**
     * Add a source root for the future library.
     * @param rootPath root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder sourceRoot(@NotNull String rootPath) {
      mySourceRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Add a javadoc root for the future library.
     * @param root root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder javaDocRoot(@NotNull VirtualFile root) {
      myJavaDocRoots.add(root);
      return this;
    }

    /**
     * Add a javadoc root for the future library.
     * @param rootPath root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder javaDocRoot(@NotNull String rootPath) {
      myJavaDocRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Add an external annotations root for the future library.
     * @param rootPath root to add
     * @return this builder
     */
    @NotNull
    public LibraryBuilder externalAnnotationsRoot(@NotNull String rootPath) {
      myExternalAnnotationsRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Creates the actual library and registers it within given {@link ModifiableRootModel}. Presumably this method
     * is called inside {@link ModuleRootModificationUtil#updateModel(Module, com.intellij.util.Consumer)}.
     *
     * @param model a model to register the library in.
     * @return a library
     */
    @NotNull
    public Library addTo(@NotNull ModifiableRootModel model) {
      return addProjectLibrary(model, myName, myClassesRoots, mySourceRoots, myJavaDocRoots, myExternalAnnotationsRoots);
    }

    /**
     * Creates the actual library and registers it within given {@link Module}. Do not call this inside
     * {@link LightProjectDescriptor#configureModule(Module, ModifiableRootModel, ContentEntry)};
     * use {@link #addTo(ModifiableRootModel)} instead.
     *
     * @param module a module to register the library in.
     * @return a library
     */
    @NotNull
    public Library addTo(@NotNull Module module) {
      Ref<Library> result = Ref.create();
      ModuleRootModificationUtil.updateModel(module, model -> result.set(addTo(model)));
      return result.get();
    }

  }
}