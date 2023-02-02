// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.java.JavaResourceRootType;
import org.jetbrains.jps.model.java.JavaSourceRootType;
import org.jetbrains.jps.model.module.JpsModuleSourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

@HeavyPlatformTestCase.WrapInCommand
public class DirectoryIndexTest extends DirectoryIndexTestCase {
  private Module myModule2, myModule3;
  private VirtualFile myRootVFile;
  private VirtualFile myModule1Dir, myModule2Dir, myModule3Dir;
  private VirtualFile mySrcDir1, mySrcDir2;
  private SourceFolder mySrcDir1Folder, mySrcDir2Folder;
  private VirtualFile myTestSrc1;
  private SourceFolder myTestSrc1Folder;
  private VirtualFile myPack1Dir, myPack2Dir;
  private VirtualFile myFileLibDir, myFileLibSrc, myFileLibCls;
  private VirtualFile myLibAdditionalOutsideDir, myLibAdditionalOutsideSrcDir, myLibAdditionalOutsideExcludedDir, myLibAdditionalOutsideClsDir;
  private VirtualFile myLibDir, myLibSrcDir, myLibClsDir;
  private VirtualFile myLibAdditionalDir, myLibAdditionalSrcDir, myLibAdditionalSrcFile, myLibAdditionalExcludedDir, myLibAdditionalClsDir, myLibAdditionalClsFile;
  private VirtualFile myExcludeDir;
  private VirtualFile myResDir, myTestResDir;
  private SourceFolder myResDirFolder, myTestResDirFolder;
  private VirtualFile myExcludedLibSrcDir, myExcludedLibClsDir;

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();

    ApplicationManager.getApplication().runWriteAction(() -> {
      /*
        root
            lib
                file.src
                file.cls
            additional-lib
                src
                excluded
                cls
            module1
                src1
                    pack1
                    testSrc
                        pack2
                res
                testRes
                lib
                    src
                      exc
                    cls
                      exc
                additional-lib
                    src
                    a.txt
                    excluded
                    cls
                module2
                    src2
                        CVS
                        excluded
            module3
            out
                module1
      */
      myRootVFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(root);
      assertNotNull(myRootVFile);

      myFileLibDir = createChildDirectory(myRootVFile, "lib");
      myFileLibSrc = createChildData(myFileLibDir, "file.src");
      myFileLibCls = createChildData(myFileLibDir, "file.cls");
      myLibAdditionalOutsideDir = createChildDirectory(myRootVFile, "additional-lib");
      myLibAdditionalOutsideSrcDir = createChildDirectory(myLibAdditionalOutsideDir, "src");
      myLibAdditionalOutsideExcludedDir = createChildDirectory(myLibAdditionalOutsideDir, "excluded");
      myLibAdditionalOutsideClsDir = createChildDirectory(myLibAdditionalOutsideDir, "cls");
      myModule1Dir = createChildDirectory(myRootVFile, "module1");
      mySrcDir1 = createChildDirectory(myModule1Dir, "src1");
      myPack1Dir = createChildDirectory(mySrcDir1, "pack1");
      myTestSrc1 = createChildDirectory(mySrcDir1, "testSrc");
      myPack2Dir = createChildDirectory(myTestSrc1, "pack2");
      myResDir = createChildDirectory(myModule1Dir, "res");
      myTestResDir = createChildDirectory(myModule1Dir, "testRes");

      myLibDir = createChildDirectory(myModule1Dir, "lib");
      myLibSrcDir = createChildDirectory(myLibDir, "src");
      myExcludedLibSrcDir = createChildDirectory(myLibSrcDir, "exc");
      myLibAdditionalDir = createChildDirectory(myModule1Dir, "additional-lib");
      myLibAdditionalSrcDir = createChildDirectory(myLibAdditionalDir, "src");
      myLibAdditionalSrcFile = createChildData(myLibAdditionalDir, "a.txt");
      myLibAdditionalExcludedDir = createChildDirectory(myLibAdditionalDir, "excluded");
      myLibAdditionalClsDir = createChildDirectory(myLibAdditionalDir, "cls");
      myLibAdditionalClsFile = createChildDirectory(myLibAdditionalDir, "file.cls");
      myLibClsDir = createChildDirectory(myLibDir, "cls");
      myExcludedLibClsDir = createChildDirectory(myLibClsDir, "exc");
      myModule2Dir = createChildDirectory(myModule1Dir, "module2");
      mySrcDir2 = createChildDirectory(myModule2Dir, "src2");
      myExcludeDir = createChildDirectory(mySrcDir2, "excluded");

      myModule3Dir = createChildDirectory(myRootVFile, "module3");


      // fill roots of module1
      {
        ModuleRootModificationUtil.setModuleSdk(myModule, null);
        PsiTestUtil.addContentRoot(myModule, myModule1Dir);
        mySrcDir1Folder = PsiTestUtil.addSourceRoot(myModule, mySrcDir1);
        myTestSrc1Folder = PsiTestUtil.addSourceRoot(myModule, myTestSrc1, true);
        myResDirFolder = PsiTestUtil.addSourceRoot(myModule, myResDir, JavaResourceRootType.RESOURCE);
        myTestResDirFolder = PsiTestUtil.addSourceRoot(myModule, myTestResDir, JavaResourceRootType.TEST_RESOURCE);

        ModuleRootModificationUtil.addModuleLibrary(myModule, "lib.js",
                                                    Collections.singletonList(myFileLibCls.getUrl()), Collections
                                                      .singletonList(myFileLibSrc.getUrl()));
        PsiTestUtil.addExcludedRoot(myModule, myExcludedLibClsDir);
        PsiTestUtil.addExcludedRoot(myModule, myExcludedLibSrcDir);
      }

      // fill roots of module2
      {
        myModule2 = createJavaModuleWithContent(getProject(), "module2", myModule2Dir);

        PsiTestUtil.addContentRoot(myModule2, myModule2Dir);
        mySrcDir2Folder = PsiTestUtil.addSourceRoot(myModule2, mySrcDir2);
        PsiTestUtil.addExcludedRoot(myModule2, myExcludeDir);
        ModuleRootModificationUtil.addModuleLibrary(myModule2, "lib",
                                                    Collections.singletonList(myLibClsDir.getUrl()), Collections.singletonList(myLibSrcDir.getUrl()),
                                                    Arrays.asList(myExcludedLibClsDir.getUrl(), myExcludedLibSrcDir.getUrl()), DependencyScope.COMPILE, true);
      }

      ExtensionTestUtil.maskExtensions(AdditionalLibraryRootsProvider.EP_NAME, Collections.<AdditionalLibraryRootsProvider>singletonList(new AdditionalLibraryRootsProvider() {
                                          @NotNull
                                          @Override
                                          public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
                                            return myProject == project ? Collections.singletonList(
                                              new JavaSyntheticLibrary(
                                                "test",
                                                List.of(myLibAdditionalSrcDir, myLibAdditionalOutsideSrcDir),
                                                List.of(myLibAdditionalClsDir, myLibAdditionalOutsideClsDir),
                                                ContainerUtil.newHashSet(myLibAdditionalExcludedDir, myLibAdditionalOutsideExcludedDir)
                                              )
                                            ) : Collections.emptyList();
                                          }
                                        }), getTestRootDisposable());

      // fill roots of module3
      {
        myModule3 = createJavaModuleWithContent(getProject(), "module3", myModule3Dir);

        PsiTestUtil.addContentRoot(myModule3, myModule3Dir);
        ModuleRootModificationUtil.addDependency(myModule3, myModule2);
      }
    });

    // to not interfere with previous test firing vfs events
    VirtualFileManager.getInstance().syncRefresh();
  }

  @Override
  protected void tearDown() throws Exception {
    myModule2 = null;
    myModule3 = null;
    super.tearDown();
  }

  private static OrderEntry[] toArray(Collection<OrderEntry> orderEntries) {
    return orderEntries.toArray(OrderEntry.EMPTY_ARRAY);
  }

  public void testSyntheticLibraryInContent() {
    ModuleRootModificationUtil.addContentRoot(myModule, FileUtil.toSystemIndependentName(myModule1Dir.getPath()));
    checkInfo(myLibAdditionalDir, myModule, false, false, null, null, null);
    checkInfo(myLibAdditionalSrcDir, myModule, false, true, "", null, null);
    checkInfo(myLibAdditionalClsDir, myModule, true, false, "", null, null);
    checkInfo(myLibAdditionalExcludedDir, myModule, false, false, null, null, null);
    assertInProject(myLibAdditionalExcludedDir);
    assertIndexableContent(Arrays.asList(myLibAdditionalSrcDir, myLibAdditionalSrcFile, myLibAdditionalExcludedDir, myLibAdditionalClsDir, myLibAdditionalClsFile), null);
  }

  public void testFileContentRootsModifications() {
    assertNotInProject(myRootVFile);
    VirtualFile temp = createChildDirectory(myRootVFile, "temp");

    VirtualFile fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    assertNotInProject(fileSourceRoot);

    PsiTestUtil.addContentRoot(myModule, fileSourceRoot);
    SourceFolder fileSourceFolder = PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    checkInfo(fileSourceRoot, myModule, false, false, "", fileSourceFolder, JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and recreate
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    checkInfo(fileSourceRoot, myModule, false, false, "", fileSourceFolder, JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and move from another dir
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(temp, "fileSourceRoot.txt");
    assertNotInProject(fileSourceRoot);
    move(fileSourceRoot, myRootVFile);
    checkInfo(fileSourceRoot, myModule, false, false, "", fileSourceFolder, JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and copy from another dir
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(temp, "fileSourceRoot.txt");
    assertNotInProject(fileSourceRoot);
    fileSourceRoot = copy(fileSourceRoot, myRootVFile, "fileSourceRoot.txt");
    checkInfo(fileSourceRoot, myModule, false, false, "", fileSourceFolder, JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));

    // delete and rename from another file
    VfsTestUtil.deleteFile(fileSourceRoot);
    assertNotInProject(fileSourceRoot);
    assertFalse(myFileIndex.isInContent(fileSourceRoot));
    assertFalse(myFileIndex.isInSource(fileSourceRoot));
    fileSourceRoot = createChildData(myRootVFile, "temp_file.txt");
    assertNotInProject(fileSourceRoot);
    rename(fileSourceRoot, "fileSourceRoot.txt");
    checkInfo(fileSourceRoot, myModule, false, false, "", fileSourceFolder, JavaSourceRootType.SOURCE, myModule);
    assertTrue(myFileIndex.isInContent(fileSourceRoot));
    assertTrue(myFileIndex.isInSource(fileSourceRoot));
  }

  private void checkInfo(VirtualFile file,
                         @Nullable Module module,
                         boolean isInLibraryClasses,
                         boolean isInLibrarySource,
                         @Nullable String packageName,
                         @Nullable SourceFolder moduleSourceFolder,
                         @Nullable final JpsModuleSourceRootType<?> moduleSourceRootType,
                         Module... modulesOfOrderEntries) {
    DirectoryInfo info = assertInProject(file);
    assertEquals(module, info.getModule());
    if (moduleSourceFolder != null || moduleSourceRootType != null) {
      assertTrue("isInModuleSource", info.isInModuleSource(file));
      assertEquals(moduleSourceFolder, myIndex.getSourceRootFolder(info));
      assertEquals(moduleSourceRootType, myIndex.getSourceRootType(info));
    }
    else {
      assertFalse("isInModuleSource", info.isInModuleSource(file));
    }
    assertEquals(isInLibraryClasses, info.hasLibraryClassRoot());
    assertEquals(isInLibrarySource, info.isInLibrarySource(file));
    assertEquals(isInLibraryClasses || isInLibrarySource, myFileIndex.isInLibrary(file));

    if (file.isDirectory()) {
      assertEquals(packageName, myFileIndex.getPackageNameByDirectory(file));
    }

    List<OrderEntry> orderEntries = myFileIndex.getOrderEntriesForFile(file);
    assertEquals(Arrays.toString(toArray(orderEntries)), modulesOfOrderEntries.length, toArray(orderEntries).length);
    for (Module aModule : modulesOfOrderEntries) {
      OrderEntry found = ModuleFileIndexImpl.findOrderEntryWithOwnerModule(aModule, orderEntries);
      assertNotNull("not found: " + aModule + " in " + Arrays.toString(toArray(orderEntries)), found);
    }
  }

  public void testUnrelatedDirectoriesCreationMustNotLeadToDirectoryIndexRebuildToImproveCheckoutSpeed() {
    VirtualFile root = ModuleRootManager.getInstance(myModule).getContentRoots()[0];
    WriteAction.run(()->ModuleRootModificationUtil.updateModel(myModule, model -> {
      ContentEntry rootEntry = model.getContentEntries()[0];
      rootEntry.addSourceFolder(createChildDirectory(root, "extsrc"), false);
    }));

    RootIndex rootIndex = myIndex.getRootIndex();

    VirtualFile xxx = createChildDirectory(root, "xxx");
    assertFalse(ProjectFileIndex.getInstance(getProject()).isInSource(xxx));
    delete(xxx);
    assertSame(rootIndex, myIndex.getRootIndex());
  }

  public void testSourceRootResidingUnderExcludedDirectoryMustBeIndexed() throws IOException {
    VirtualFile contentDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDir("module"));

    Module module = createJavaModuleWithContent(getProject(), "module", contentDir);

    ApplicationManager.getApplication().runWriteAction(() -> {
      VirtualFile excludedDir = createChildDirectory(contentDir, "excluded");
      VirtualFile sourcesDir = createChildDirectory(excludedDir, "sources");
      createChildData(sourcesDir, "A.java");

      PsiTestUtil.addContentRoot(module, contentDir);
      PsiTestUtil.addExcludedRoot(module, excludedDir);
      PsiTestUtil.addSourceRoot(module, sourcesDir);
    });

    VirtualFile aJava = contentDir.findChild("excluded").findChild("sources").findChild("A.java");
    assertIndexableContent(Collections.singletonList(aJava), Collections.emptyList());
  }
}