// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
import com.intellij.openapi.roots.impl.libraries.ProjectLibraryTable;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.roots.libraries.LibraryTable;
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

public class PsiTestUtil {
  public static VirtualFile createTestProjectStructure(Project project,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<? super File> filesToDelete) throws Exception {
    return createTestProjectStructure(project, module, rootPath, filesToDelete, true);
  }

  public static VirtualFile createTestProjectStructure(Project project, Module module, Collection<? super File> filesToDelete) throws IOException {
    return createTestProjectStructure(project, module, null, filesToDelete, true);
  }

  public static VirtualFile createTestProjectStructure(Project project,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<? super File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    VirtualFile vDir = createTestProjectStructure(module, rootPath, filesToDelete, addProjectRoots);
    PsiDocumentManager.getInstance(project).commitAllDocuments();
    return vDir;
  }

  public static VirtualFile createTestProjectStructure(Module module,
                                                       String rootPath,
                                                       Collection<? super File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    return createTestProjectStructure("unitTest", module, rootPath, filesToDelete, addProjectRoots);
  }

  public static VirtualFile createTestProjectStructure(String tempName,
                                                       Module module,
                                                       String rootPath,
                                                       Collection<? super File> filesToDelete,
                                                       boolean addProjectRoots) throws IOException {
    File dir = FileUtil.createTempDirectory(tempName, null, false);
    filesToDelete.add(dir);

    VirtualFile vDir = LocalFileSystem.getInstance().refreshAndFindFileByPath(dir.getCanonicalPath().replace(File.separatorChar, '/'));
    assert vDir != null && vDir.isDirectory() : dir;
    PlatformTestCase.synchronizeTempDirVfs(vDir);

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

  public static SourceFolder addSourceContentToRoots(Module module, @NotNull VirtualFile vDir) {
    return addSourceContentToRoots(module, vDir, false);
  }

  public static SourceFolder addSourceContentToRoots(Module module, @NotNull VirtualFile vDir, boolean testSource) {
    Ref<SourceFolder> result = Ref.create();
    ModuleRootModificationUtil.updateModel(module, model -> result.set(model.addContentEntry(vDir).addSourceFolder(vDir, testSource)));
    return result.get();
  }

  public static SourceFolder addSourceRoot(Module module, VirtualFile vDir) {
    return addSourceRoot(module, vDir, false);
  }

  public static SourceFolder addSourceRoot(Module module, VirtualFile vDir, boolean isTestSource) {
    return addSourceRoot(module, vDir, isTestSource ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
  }

  public static <P extends JpsElement> SourceFolder addSourceRoot(Module module, VirtualFile vDir, @NotNull JpsModuleSourceRootType<P> rootType) {
    return addSourceRoot(module, vDir, rootType, rootType.createDefaultProperties());
  }

  public static <P extends JpsElement> SourceFolder addSourceRoot(Module module,
                                                          VirtualFile vDir,
                                                          @NotNull JpsModuleSourceRootType<P> rootType,
                                                          P properties) {
    Ref<SourceFolder> result = Ref.create();
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntry(model, vDir);
      if (entry == null) entry = model.addContentEntry(vDir);
      result.set(entry.addSourceFolder(vDir, rootType, properties));
    });
    return result.get();
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

  public static void removeExcludedRoot(Module module, VirtualFile root) {
    ModuleRootModificationUtil.updateModel(module, model -> {
      ContentEntry entry = findContentEntryWithAssertion(model, root);
      entry.removeExcludeFolder(root.getUrl());
    });
  }

  public static void checkErrorElements(PsiElement element) {
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

  public static void checkFileStructure(PsiFile file) {
    compareFromAllRoots(file, f -> DebugUtil.psiTreeToString(f, false));
  }

  private static void compareFromAllRoots(PsiFile file, Function<? super PsiFile, String> fun) {
    PsiFile dummyFile = createDummyCopy(file);

    String psiTree = StringUtil.join(file.getViewProvider().getAllFiles(), fun, "\n");
    String reparsedTree = StringUtil.join(dummyFile.getViewProvider().getAllFiles(), fun, "\n");
    assertPsiTextTreeConsistency(psiTree, reparsedTree);
  }

  private static void assertPsiTextTreeConsistency(String psiTree, String reparsedTree) {
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
  private static PsiFile createDummyCopy(PsiFile file) {
    LightVirtualFile copy = new LightVirtualFile(file.getName(), file.getText());
    copy.setOriginalFile(file.getViewProvider().getVirtualFile());
    PsiFile dummyCopy = Objects.requireNonNull(file.getManager().findFile(copy));
    if (dummyCopy instanceof PsiFileImpl) {
      ((PsiFileImpl)dummyCopy).setOriginalFile(file);
    }
    return dummyCopy;
  }

  public static void checkPsiMatchesTextIgnoringNonCode(PsiFile file) {
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
  public static LibraryBuilder newLibrary(String name) {
    return new LibraryBuilder(name);
  }

  public static void addLibrary(Module module, String libPath) {
    File file = new File(libPath);
    String libName = file.getName();
    addLibrary(module, libName, file.getParent(), libName);
  }

  public static void addLibrary(Module module, String libName, String libPath, String... jarArr) {
    ModuleRootModificationUtil.updateModel(module, model -> addLibrary(module, model, libName, libPath, jarArr));
  }
  public static void addLibrary(@NotNull Disposable parent, Module module, String libName, String libPath, String... jarArr) {
    Ref<Library> ref = new Ref<>();
    ModuleRootModificationUtil.updateModel(module, model -> ref.set(addLibrary(module, model, libName, libPath, jarArr)));
    Disposer.register(parent, () -> {
      Library library = ref.get();
      ModuleRootModificationUtil.updateModel(module, model -> {
        LibraryOrderEntry entry = model.findLibraryOrderEntry(library);
        if (entry != null) {
          model.removeOrderEntry(entry);
        }
      });
      WriteCommandAction.runWriteCommandAction(null, ()-> {
        LibraryTable table = ProjectLibraryTable.getInstance(module.getProject());
        LibraryTable.ModifiableModel model = table.getModifiableModel();
        model.removeLibrary(library);
        model.commit();
      });
    });
  }

  public static void addProjectLibrary(Module module, String libName, List<String> classesRootPaths) {
    List<VirtualFile> roots = getLibraryRoots(classesRootPaths);
    addProjectLibrary(module, libName, roots, Collections.emptyList());
  }

  @NotNull
  private static List<VirtualFile> getLibraryRoots(List<String> classesRootPaths) {
    return ContainerUtil.map(classesRootPaths, path -> VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(path))));
  }

  public static void addProjectLibrary(ModifiableRootModel model, String libName, List<String> classesRootPaths) {
    List<VirtualFile> roots = getLibraryRoots(classesRootPaths);
    addProjectLibrary(model, libName, roots, Collections.emptyList(), Collections.emptyList());
  }

  public static void addProjectLibrary(Module module, String libName, VirtualFile... classesRoots) {
    addProjectLibrary(module, libName, Arrays.asList(classesRoots), Collections.emptyList());
  }

  public static Library addProjectLibrary(Module module, String libName, List<? extends VirtualFile> classesRoots, List<? extends VirtualFile> sourceRoots) {
    Ref<Library> result = Ref.create();
    ModuleRootModificationUtil.updateModel(
      module, model -> result.set(addProjectLibrary(model, libName, classesRoots, sourceRoots, Collections.emptyList())));
    return result.get();
  }

  @NotNull
  private static Library addProjectLibrary(ModifiableRootModel model,
                                           String libName,
                                           List<? extends VirtualFile> classesRoots,
                                           List<? extends VirtualFile> sourceRoots,
                                           List<? extends VirtualFile> javaDocs) {
    LibraryTable libraryTable = ProjectLibraryTable.getInstance(model.getProject());
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

  @NotNull
  public static Library addLibrary(Module module,
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
    return addProjectLibrary(model, libName, classesRoots, Collections.emptyList(), Collections.emptyList());
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
      compareStubTexts(e);
    }
  }

  public static void compareStubTexts(@NotNull StubTextInconsistencyException e) {
    assertPsiTextTreeConsistency(e.getStubsFromPsi(), e.getStubsFromText());
    throw e;
  }

  public static void checkPsiStructureWithCommit(@NotNull PsiFile psiFile, Consumer<? super PsiFile> checker) {
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
    
    private LibraryBuilder(String name) {
      myName = name;
    }

    /**
     * Add a classes root for the future library. 
     * @param root root to add
     * @return this builder
     */
    public LibraryBuilder classesRoot(VirtualFile root) {
      myClassesRoots.add(root);
      return this;
    }

    /**
     * Add a classes root for the future library. 
     * @param rootPath root to add
     * @return this builder
     */
    public LibraryBuilder classesRoot(String rootPath) {
      myClassesRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Add a source root for the future library. 
     * @param root root to add
     * @return this builder
     */
    public LibraryBuilder sourceRoot(VirtualFile root) {
      mySourceRoots.add(root);
      return this;
    }

    /**
     * Add a source root for the future library. 
     * @param rootPath root to add
     * @return this builder
     */
    public LibraryBuilder sourceRoot(String rootPath) {
      mySourceRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Add a javadoc root for the future library. 
     * @param root root to add
     * @return this builder
     */
    public LibraryBuilder javaDocRoot(VirtualFile root) {
      myJavaDocRoots.add(root);
      return this;
    }

    /**
     * Add a javadoc root for the future library. 
     * @param rootPath root to add
     * @return this builder
     */
    public LibraryBuilder javaDocRoot(String rootPath) {
      myJavaDocRoots.add(VirtualFileManager.getInstance().refreshAndFindFileByUrl(VfsUtil.getUrlForLibraryRoot(new File(rootPath))));
      return this;
    }

    /**
     * Creates the actual library and registers it within given {@link ModifiableRootModel}. Presumably this method
     * is called inside {@link ModuleRootModificationUtil#updateModel(Module, com.intellij.util.Consumer)}.
     * 
     * @param model a model to register the library in.
     * @return a library
     */
    public Library addTo(ModifiableRootModel model) {
      return addProjectLibrary(model, myName, myClassesRoots, mySourceRoots, myJavaDocRoots);
    }

    /**
     * Creates the actual library and registers it within given {@link Module}. Do not call this inside 
     * {@link LightProjectDescriptor#configureModule(Module, ModifiableRootModel, ContentEntry)}; 
     * use {@link #addTo(ModifiableRootModel)} instead.
     * 
     * @param module a module to register the library in.
     * @return a library
     */
    public Library addTo(Module module) {
      Ref<Library> result = Ref.create();
      ModuleRootModificationUtil.updateModel(module, model -> result.set(addTo(model)));
      return result.get();
    }
    
  }
}