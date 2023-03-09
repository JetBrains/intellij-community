// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.module.ModifiableModuleModel;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.module.ModuleTypeId;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.RootsChangeRescanningInfo;
import com.intellij.openapi.roots.*;
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.*;
import com.intellij.testFramework.ExtensionTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.util.ConcurrencyUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.java.JavaResourceRootType;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class IndexableFilesIndexTest extends IndexableFilesIndexTestCase {
  private Module myModule2, myModule3;
  private VirtualFile myRootVFile;
  private VirtualFile myModule1Dir, myModule2Dir, myModule3Dir;
  private VirtualFile mySrcDir1, mySrcDir2;
  private VirtualFile myTestSrc1;
  private VirtualFile myPack1Dir, myPack2Dir;
  private VirtualFile myFileLibDir, myFileLibSrc, myFileLibCls;
  private VirtualFile myLibAdditionalOutsideDir, myLibAdditionalOutsideSrcDir, myLibAdditionalOutsideExcludedDir,
    myLibAdditionalOutsideClsDir;
  private VirtualFile myLibDir, myLibSrcDir, myLibClsDir;
  private VirtualFile myLibAdditionalDir, myLibAdditionalSrcDir, myLibAdditionalExcludedDir, myLibAdditionalClsDir;
  private VirtualFile myCvsDir;
  private VirtualFile myExcludeDir;
  private VirtualFile myOutputDir;
  private VirtualFile myModule1OutputDir;
  private VirtualFile myResDir, myTestResDir;
  private VirtualFile myExcludedLibSrcDir, myExcludedLibClsDir;

  @Override
  protected boolean runInDispatchThread() {
    return false;
  }

  @Override
  protected void setUp() throws Exception {
    super.setUp();

    final File root = createTempDirectory();

    WriteAction.runAndWait(() -> {
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
      myLibAdditionalExcludedDir = createChildDirectory(myLibAdditionalDir, "excluded");
      myLibAdditionalClsDir = createChildDirectory(myLibAdditionalDir, "cls");
      myLibClsDir = createChildDirectory(myLibDir, "cls");
      myExcludedLibClsDir = createChildDirectory(myLibClsDir, "exc");
      myModule2Dir = createChildDirectory(myModule1Dir, "module2");
      mySrcDir2 = createChildDirectory(myModule2Dir, "src2");
      myCvsDir = createChildDirectory(mySrcDir2, "CVS");
      myExcludeDir = createChildDirectory(mySrcDir2, "excluded");

      myModule3Dir = createChildDirectory(myRootVFile, "module3");

      myOutputDir = createChildDirectory(myRootVFile, "out");
      myModule1OutputDir = createChildDirectory(myOutputDir, "module1");

      getCompilerProjectExtension().setCompilerOutputUrl(myOutputDir.getUrl());

      // fill roots of module1
      {
        ModuleRootModificationUtil.setModuleSdk(myModule, null);
        PsiTestUtil.addContentRoot(myModule, myModule1Dir);
        PsiTestUtil.addSourceRoot(myModule, myResDir, JavaResourceRootType.RESOURCE);
        PsiTestUtil.addSourceRoot(myModule, myTestResDir, JavaResourceRootType.TEST_RESOURCE);

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
        PsiTestUtil.addExcludedRoot(myModule2, myExcludeDir);
        ModuleRootModificationUtil.addModuleLibrary(myModule2, "lib",
                                                    Collections.singletonList(myLibClsDir.getUrl()),
                                                    Collections.singletonList(myLibSrcDir.getUrl()),
                                                    Arrays.asList(myExcludedLibClsDir.getUrl(), myExcludedLibSrcDir.getUrl()),
                                                    DependencyScope.COMPILE, true);
      }

      ExtensionTestUtil.maskExtensions(AdditionalLibraryRootsProvider.EP_NAME,
                                       Collections.<AdditionalLibraryRootsProvider>singletonList(new AdditionalLibraryRootsProvider() {
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
    ApplicationManager.getApplication().invokeAndWait(() -> VirtualFileManager.getInstance().syncRefresh());
  }

  @Override
  protected void tearDown() throws Exception {
    myModule2 = null;
    myModule3 = null;
    ApplicationManager.getApplication().invokeAndWait(() -> {
      try {
        super.tearDown();
      }
      catch (Exception e) {
        throw new RuntimeException(e);
      }
    });
  }

  private CompilerProjectExtension getCompilerProjectExtension() {
    final CompilerProjectExtension instance = CompilerProjectExtension.getInstance(myProject);
    assertNotNull(instance);
    return instance;
  }

  public void testDirInfos() {
    assertNotIndexed(myRootVFile);

    // beware: files in directory index
    checkInfo(myFileLibSrc);
    checkInfo(myFileLibCls);

    checkInfo(myLibAdditionalOutsideSrcDir);
    checkInfo(myLibAdditionalOutsideClsDir);
    assertNotIndexed(myLibAdditionalOutsideExcludedDir);

    checkInfo(myModule1Dir);
    checkInfo(mySrcDir1);
    checkInfo(myPack1Dir);
    checkInfo(myTestSrc1);
    checkInfo(myPack2Dir);
    checkInfo(myResDir);
    checkInfo(myTestResDir);

    checkInfo(myLibDir);
    checkInfo(myLibSrcDir);
    checkInfo(myLibClsDir);

    checkInfo(myModule2Dir);
    checkInfo(mySrcDir2);
    assertNotIndexed(myCvsDir);
    assertNotIndexed(myExcludeDir);
    assertNotIndexed(myExcludedLibClsDir);
    assertNotIndexed(myExcludedLibSrcDir);

    checkInfo(myModule3Dir);

    VirtualFile cvs = createChildDirectory(myPack1Dir, "CVS");
    assertNotIndexed(cvs);
  }

  public void testChangeIgnoreList() {
    VirtualFile newDir = createChildDirectory(myModule1Dir, "newDir");

    assertIndexed(newDir);

    final FileTypeManagerEx fileTypeManager = (FileTypeManagerEx)FileTypeManager.getInstance();
    final String list = fileTypeManager.getIgnoredFilesList();
    try {
      final String list1 = list + ";" + "newDir";
      WriteAction.runAndWait(() -> fileTypeManager.setIgnoredFilesList(list1));
      assertNotIndexed(newDir);
    }
    finally {
      WriteAction.runAndWait(() -> fileTypeManager.setIgnoredFilesList(list));
      assertIndexed(newDir);
    }
  }

  public void testIgnoredFile() {
    VirtualFile ignoredFile = createChildData(myModule1Dir, "CVS");
    assertNotIndexed(ignoredFile);
  }

  public void testModuleUnderIgnoredDir() {
    final VirtualFile ignored = createChildDirectory(myRootVFile, ".git");
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignored));
    assertNotIndexed(ignored);
    final VirtualFile module4 = createChildDirectory(ignored, "module4");
    assertFalse(FileTypeManager.getInstance().isFileIgnored(module4));
    assertNotIndexed(module4);

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      Module module = moduleManager.newModule(myRootVFile.getPath() + "/newModule.iml", ModuleTypeId.JAVA_MODULE);
      PsiTestUtil.addContentRoot(module, module4);
    });
    assertNotIndexed(ignored);
    checkInfo(module4);
  }

  public void testModuleInIgnoredDir() {
    final VirtualFile ignored = createChildDirectory(myRootVFile, ".git");
    assertTrue(FileTypeManager.getInstance().isFileIgnored(ignored));

    WriteCommandAction.writeCommandAction(getProject()).run(() -> {
      ModuleManager moduleManager = ModuleManager.getInstance(myProject);
      ModifiableModuleModel model = moduleManager.getModifiableModel();
      model.disposeModule(myModule);
      model.disposeModule(myModule2);
      model.disposeModule(myModule3);
      model.commit();
      Module module = moduleManager.newModule(myRootVFile.getPath() + "/newModule.iml", ModuleTypeId.JAVA_MODULE);
      PsiTestUtil.addContentRoot(module, ignored);
    });
    checkInfo(ignored);
  }

  public void testExcludedDirsInLibraries() {
    assertNotIndexed(myExcludedLibClsDir);
    assertNotIndexed(myExcludedLibSrcDir);
  }

  public void testExplicitExcludeOfInner() {
    PsiTestUtil.addExcludedRoot(myModule, myModule2Dir);

    checkInfo(myModule2Dir);
    checkInfo(mySrcDir2);
  }

  public void testResettingProjectOutputPath() {
    VirtualFile output1 = createChildDirectory(myModule1Dir, "output1");
    VirtualFile output2 = createChildDirectory(myModule1Dir, "output2");

    assertIndexed(output1);
    assertIndexed(output2);

    getCompilerProjectExtension().setCompilerOutputUrl(output1.getUrl());
    fireRootsChanged();

    assertNotIndexed(output1);
    assertIndexed(output2);

    getCompilerProjectExtension().setCompilerOutputUrl(output2.getUrl());
    fireRootsChanged();

    assertIndexed(output1);
    assertNotIndexed(output2);
  }

  private void fireRootsChanged() {
    WriteAction.runAndWait(() -> ProjectRootManagerEx.getInstanceEx(getProject()).
      makeRootsChange(EmptyRunnable.getInstance(), RootsChangeRescanningInfo.NO_RESCAN_NEEDED));
  }

  public void testModuleSourceAsLibrarySource() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, "someLib", Collections.emptyList(),
                                                Collections.singletonList(mySrcDir1.getUrl()));
    //what if same files for multiple iterators?
    checkInfo(mySrcDir1);
    checkInfo(myTestSrc1);
  }

  public void testModuleSourceAsLibraryClasses() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, "someLib", Collections.singletonList(mySrcDir1.getUrl()),
                                                Collections.emptyList());
    checkInfo(mySrcDir1);
  }

  public void testModulesWithSameSourceContentRoot() {
    // now our API allows this (ReformatCodeActionTest), although UI doesn't. Maybe API shouldn't allow it as well?
    PsiTestUtil.addContentRoot(myModule2, myModule1Dir);
    PsiTestUtil.addSourceRoot(myModule2, mySrcDir1);

    checkInfo(myModule1Dir);
    checkInfo(mySrcDir1);
    checkInfo(myTestSrc1);
    checkInfo(myResDir);

    checkInfo(mySrcDir2);
  }

  public void testModuleWithSameSourceRoot() {
    PsiTestUtil.addSourceRoot(myModule2, mySrcDir1);
    checkInfo(mySrcDir1);
    checkInfo(myTestSrc1);
  }

  public void testModuleContentUnderSourceRoot() {
    PsiTestUtil.addContentRoot(myModule2, myPack1Dir);
    checkInfo(myPack1Dir);
  }

  public void testSameSourceAndOutput() {
    PsiTestUtil.setCompilerOutputPath(myModule, mySrcDir1.getUrl(), false);
    assertNotIndexed(mySrcDir1);
  }

  public void testExcludedDirShouldBeExcludedRightAfterItsCreation() {
    VirtualFile excluded = createChildDirectory(myModule1Dir, "excluded");
    VirtualFile projectOutput = createChildDirectory(myModule1Dir, "projectOutput");
    VirtualFile module2Output = createChildDirectory(myModule1Dir, "module2Output");
    VirtualFile module2TestOutput = createChildDirectory(myModule2Dir, "module2TestOutput");

    assertIndexed(excluded);
    assertIndexed(projectOutput);
    assertIndexed(module2Output);
    assertIndexed(module2TestOutput);

    getCompilerProjectExtension().setCompilerOutputUrl(projectOutput.getUrl());

    PsiTestUtil.addExcludedRoot(myModule, excluded);
    PsiTestUtil.setCompilerOutputPath(myModule2, module2Output.getUrl(), false);
    PsiTestUtil.setCompilerOutputPath(myModule2, module2TestOutput.getUrl(), true);
    PsiTestUtil.setExcludeCompileOutput(myModule2, true);

    assertNotIndexed(excluded);
    assertNotIndexed(projectOutput);
    assertNotIndexed(module2Output);
    assertNotIndexed(module2TestOutput);

    VfsTestUtil.deleteFile(excluded);
    VfsTestUtil.deleteFile(projectOutput);
    VfsTestUtil.deleteFile(module2Output);
    VfsTestUtil.deleteFile(module2TestOutput);

    final List<VirtualFile> created = new ArrayList<>();
    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent e) {
        VirtualFile file = e.getFile();
        //assertNotIndexed(file);
        created.add(file);
      }
    };
    VirtualFileManager.getInstance().addVirtualFileListener(l, getTestRootDisposable());

    excluded = createChildDirectory(myModule1Dir, excluded.getName());
    assertNotIndexed(excluded);

    projectOutput = createChildDirectory(myModule1Dir, projectOutput.getName());
    assertNotIndexed(projectOutput);

    module2Output = createChildDirectory(myModule1Dir, module2Output.getName());
    assertNotIndexed(module2Output);

    module2TestOutput = createChildDirectory(myModule2Dir, module2TestOutput.getName());
    assertNotIndexed(module2TestOutput);

    assertEquals(created.toString(), 4, created.size());
  }

  public void testExcludesShouldBeRecognizedRightOnRefresh() throws ExecutionException, InterruptedException {
    final VirtualFile dir = createChildDirectory(myModule1Dir, "dir");
    final VirtualFile excluded = createChildDirectory(dir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule, excluded);
    VfsTestUtil.deleteFile(dir);
    List<Future<?>> futures = new ArrayList<>();

    boolean created = new File(myModule1Dir.getPath(), "dir/excluded/foo").mkdirs();
    assertTrue(created);

    VirtualFileListener l = new VirtualFileListener() {
      @Override
      public void fileCreated(@NotNull VirtualFileEvent e) {
        assertEquals("dir", e.getFileName());

        VirtualFile file = e.getFile();
        futures.add(ApplicationManager.getApplication().executeOnPooledThread(() -> {
          assertIndexed(file);
          assertNotIndexed(file.findFileByRelativePath("excluded"));
          assertNotIndexed(file.findFileByRelativePath("excluded/foo"));
        }));
      }
    };

    VirtualFileManager.getInstance().addVirtualFileListener(l, getTestRootDisposable());
    ApplicationManager.getApplication().invokeAndWait(() -> VirtualFileManager.getInstance().syncRefresh());
    ConcurrencyUtil.getAll(futures);
  }

  public void testProcessingNestedContentRootsOfExcludedDirsOnCreation() {
    String rootPath = myModule1Dir.getPath();
    final File f = new File(rootPath, "excludedDir/dir/anotherContentRoot");
    WriteAction.runAndWait(() -> {
      ModifiableRootModel rootModel = ModuleRootManager.getInstance(myModule).getModifiableModel();
      rootModel.getContentEntries()[0]
        .addExcludeFolder(VfsUtilCore.pathToUrl(f.getParentFile().getParent()));
      rootModel.commit();

      ModuleRootModificationUtil.addContentRoot(myModule2, FileUtil.toSystemIndependentName(f.getPath()));

      assertTrue(f.getPath(), f.exists() || f.mkdirs());
      LocalFileSystem.getInstance().refresh(false);
    });

    assertNotIndexed(LocalFileSystem.getInstance().findFileByIoFile(f.getParentFile().getParentFile()));
    assertIndexed(LocalFileSystem.getInstance().findFileByIoFile(f));
  }

  public void testSyntheticLibraryInContent() {
    ModuleRootModificationUtil.addContentRoot(myModule, FileUtil.toSystemIndependentName(myModule1Dir.getPath()));
    checkInfo(myLibAdditionalDir);
    checkInfo(myLibAdditionalSrcDir);
    checkInfo(myLibAdditionalClsDir);
    checkInfo(myLibAdditionalExcludedDir);
    assertIndexed(myLibAdditionalExcludedDir);
  }

  public void testLibraryDirInContent() {
    ModuleRootModificationUtil.addModuleLibrary(myModule, myModule1Dir.getUrl());

    checkInfo(myModule1Dir);
    checkInfo(mySrcDir1);

    checkInfo(myModule2Dir);
    checkInfo(mySrcDir2);
    checkInfo(myExcludeDir);

    checkInfo(myLibDir);
    checkInfo(myLibClsDir);

    //myModule is included into order entries instead of myModule2 because classes root for libraries dominates on source roots
    checkInfo(myLibSrcDir);

    checkInfo(myResDir);

    checkInfo(myExcludedLibSrcDir);
    checkInfo(myExcludedLibClsDir);
  }

  public void testExcludeCompilerOutputOutsideOfContentRoot() {
    String moduleOutputUrl = myModule1OutputDir.getUrl();

    VfsTestUtil.deleteFile(myOutputDir);

    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutputUrl, false);
    myOutputDir = createChildDirectory(myRootVFile, "out");
    myModule1OutputDir = createChildDirectory(myOutputDir, "module1");

    assertNotIndexed(myOutputDir);
    assertNotIndexed(myModule1OutputDir);

    PsiTestUtil.setCompilerOutputPath(myModule, moduleOutputUrl, true);
    PsiTestUtil.setCompilerOutputPath(myModule2, moduleOutputUrl, false);
    PsiTestUtil.setCompilerOutputPath(myModule2, moduleOutputUrl, true);
    PsiTestUtil.setCompilerOutputPath(myModule3, moduleOutputUrl, false);
    PsiTestUtil.setCompilerOutputPath(myModule3, moduleOutputUrl, true);

    // now no module inherits project output dir, but it still should be project-excluded
    assertNotIndexed(myOutputDir);

    // project output inside module content shouldn't be projectExcludeRoot
    VirtualFile projectOutputUnderContent = createChildDirectory(myModule1Dir, "projectOutputUnderContent");
    getCompilerProjectExtension().setCompilerOutputUrl(projectOutputUnderContent.getUrl());
    fireRootsChanged();

    assertNotIndexed(myOutputDir);
    assertNotIndexed(projectOutputUnderContent);

    VfsTestUtil.deleteFile(projectOutputUnderContent);
    projectOutputUnderContent = createChildDirectory(myModule1Dir, "projectOutputUnderContent");
    assertNotIndexed(myOutputDir);
    assertNotIndexed(projectOutputUnderContent);
  }

  public void testFileContentAndSourceRoots() {
    VirtualFile fileRoot = createChildData(myRootVFile, "fileRoot.txt");
    VirtualFile fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    VirtualFile fileTestSourceRoot = createChildData(myRootVFile, "fileTestSourceRoot.txt");

    assertNotIndexed(fileRoot);

    ContentEntry contentEntry = PsiTestUtil.addContentRoot(myModule, fileRoot);
    assertNotNull(contentEntry);
    assertEquals(fileRoot, contentEntry.getFile());
    checkInfo(fileRoot);
    assertIndexed(fileRoot);

    PsiTestUtil.addContentRoot(myModule, fileSourceRoot);
    PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    checkInfo(fileSourceRoot);

    PsiTestUtil.addContentRoot(myModule, fileTestSourceRoot);
    PsiTestUtil.addSourceRoot(myModule, fileTestSourceRoot, true);
    checkInfo(fileTestSourceRoot);

    // removing file source root
    PsiTestUtil.removeSourceRoot(myModule, fileTestSourceRoot);
    checkInfo(fileTestSourceRoot);

    // removing file content root
    PsiTestUtil.removeContentEntry(myModule, Objects.requireNonNull(contentEntry.getFile()));
    assertNotIndexed(fileRoot);
  }

  public void testFileSourceRootsUnderDirContentRoot() {
    VirtualFile fileSourceRoot = createChildData(myModule1Dir, "fileSourceRoot.txt");
    assertIndexed(fileSourceRoot);

    PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    assertIndexed(fileSourceRoot);
    checkInfo(fileSourceRoot);

    // removing file source root
    PsiTestUtil.removeSourceRoot(myModule, fileSourceRoot);
    assertIndexed(fileSourceRoot);
  }

  public void testFileModuleExcludeRootUnderDirectoryRoot() {
    VirtualFile fileExcludeRoot = createChildData(mySrcDir1, "fileExcludeRoot.txt");
    assertIndexed(fileExcludeRoot);

    PsiTestUtil.addExcludedRoot(myModule, fileExcludeRoot);
    assertNotIndexed(fileExcludeRoot);

    // removing file exclude root
    PsiTestUtil.removeExcludedRoot(myModule, fileExcludeRoot);
    assertIndexed(fileExcludeRoot);
  }

  public void testFileModuleExcludeRootUnderFileRoot() {
    VirtualFile fileRoot = createChildData(myRootVFile, "fileRoot.txt");
    PsiTestUtil.addContentRoot(myModule, fileRoot);
    checkInfo(fileRoot);

    PsiTestUtil.addExcludedRoot(myModule, fileRoot);
    assertNotIndexed(fileRoot);

    // removing file exclude root
    PsiTestUtil.removeExcludedRoot(myModule, fileRoot);
    checkInfo(fileRoot);
  }

  public void testFileLibraryInsideFolderLibrary() {
    VirtualFile file = createChildData(myLibSrcDir, "empty.txt");
    ModuleRootModificationUtil.addModuleLibrary(myModule2, "lib2",
                                                Collections.emptyList(), Collections.singletonList(file.getUrl()),
                                                Collections.emptyList(), DependencyScope.COMPILE, true);

    // same for the dir and for the file
    checkInfo(file);
    checkInfo(myLibSrcDir);
  }

  public void testFileContentRootsModifications() {
    assertNotIndexed(myRootVFile);
    VirtualFile temp = createChildDirectory(myRootVFile, "temp");

    VirtualFile fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");
    assertNotIndexed(fileSourceRoot);

    PsiTestUtil.addContentRoot(myModule, fileSourceRoot);
    PsiTestUtil.addSourceRoot(myModule, fileSourceRoot);
    checkInfo(fileSourceRoot);

    // delete and recreate
    VfsTestUtil.deleteFile(fileSourceRoot);

    fileSourceRoot = createChildData(myRootVFile, "fileSourceRoot.txt");

    // delete and move from another dir
    VfsTestUtil.deleteFile(fileSourceRoot);
    fileSourceRoot = createChildData(temp, "fileSourceRoot.txt");
    assertNotIndexed(fileSourceRoot);
    move(fileSourceRoot, myRootVFile);

    // delete and copy from another dir
    VfsTestUtil.deleteFile(fileSourceRoot);
    fileSourceRoot = createChildData(temp, "fileSourceRoot.txt");
    assertNotIndexed(fileSourceRoot);
    fileSourceRoot = copy(fileSourceRoot, myRootVFile, "fileSourceRoot.txt");

    // delete and rename from another file
    VfsTestUtil.deleteFile(fileSourceRoot);
    fileSourceRoot = createChildData(myRootVFile, "temp_file.txt");
    assertNotIndexed(fileSourceRoot);
    rename(fileSourceRoot, "fileSourceRoot.txt");
  }

  public void testSourceContentRootsUnderExcludedRoot() {
    VirtualFile contentRoot = createChildDirectory(myExcludeDir, "content");
    PsiTestUtil.addContentRoot(myModule2, contentRoot);
    checkInfo(contentRoot);

    VirtualFile sourceRoot = createChildDirectory(myExcludeDir, "src");
    PsiTestUtil.addSourceRoot(myModule2, sourceRoot);
    checkInfo(sourceRoot);

    VirtualFile contentSourceRoot = createChildDirectory(myExcludeDir, "content-src");
    PsiTestUtil.addSourceContentToRoots(myModule2, contentSourceRoot);
    checkInfo(contentSourceRoot);
  }

  public void testSourceContentRootsUnderExcludedRootUnderSourceRoot() {
    VirtualFile excluded = createChildDirectory(myModule2Dir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule2, excluded);

    VirtualFile contentRoot = createChildDirectory(excluded, "content");
    PsiTestUtil.addContentRoot(myModule2, contentRoot);
    checkInfo(contentRoot);

    VirtualFile sourceRoot = createChildDirectory(excluded, "src");
    PsiTestUtil.addSourceRoot(myModule2, sourceRoot);
    checkInfo(sourceRoot);

    VirtualFile contentSourceRoot = createChildDirectory(excluded, "content-src");
    PsiTestUtil.addSourceContentToRoots(myModule2, contentSourceRoot);
    checkInfo(contentSourceRoot);
  }

  public void testExcludedSourceRootUnderExcluded() {
    VirtualFile excluded = createChildDirectory(myModule2Dir, "excluded");
    PsiTestUtil.addExcludedRoot(myModule2, excluded);

    VirtualFile src = createChildDirectory(excluded, "src");
    PsiTestUtil.addSourceRoot(myModule2, src);
    PsiTestUtil.addExcludedRoot(myModule2, src);
    assertNotIndexed(src);
  }

  private void checkInfo(VirtualFile file) {
    assertIndexed(file);
  }

  public void testSourceRootResidingUnderExcludedDirectoryMustBeIndexed() throws IOException {
    VirtualFile contentDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(createTempDir("module"));

    Module module = createJavaModuleWithContent(getProject(), "module", contentDir);

    WriteAction.runAndWait(() -> {
      VirtualFile excludedDir = createChildDirectory(contentDir, "excluded");
      VirtualFile sourcesDir = createChildDirectory(excludedDir, "sources");
      createChildData(sourcesDir, "A.java");

      PsiTestUtil.addContentRoot(module, contentDir);
      PsiTestUtil.addExcludedRoot(module, excludedDir);
      PsiTestUtil.addSourceRoot(module, sourcesDir);
    });
  }
}
