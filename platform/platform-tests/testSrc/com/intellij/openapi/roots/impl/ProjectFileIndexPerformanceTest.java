// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots.impl;

import com.intellij.openapi.application.WriteAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.roots.ModuleRootModificationUtil;
import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.impl.libraries.LibraryEx;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.openapi.vfs.VirtualFileWithId;
import com.intellij.testFramework.LightVirtualFile;
import com.intellij.testFramework.PlatformTestUtil;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.VfsTestUtil;
import com.intellij.testFramework.junit5.TestApplication;
import com.intellij.testFramework.rules.ClassLevelProjectModelExtension;
import kotlin.Unit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@TestApplication
public class ProjectFileIndexPerformanceTest {
  @RegisterExtension
  static final ClassLevelProjectModelExtension ourProjectModel = new ClassLevelProjectModelExtension();
  static final List<VirtualFile> ourSourceFilesToTest = new ArrayList<>();
  private static VirtualFile ourProjectRoot;

  @BeforeAll
  static void initProject() throws IOException {
    WriteAction.runAndWait(() -> {
      VirtualFile fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");
      ourProjectRoot = fsRoot.createChildDirectory(ourProjectModel, ProjectFileIndexPerformanceTest.class.getSimpleName());
      VirtualFile bigModuleRoot = ourProjectRoot.createChildDirectory(ourProjectModel, "big");
      WriteAction.runAndWait(() -> {
        for (int i = 0; i < 100; i++) {
          VirtualFile directory = bigModuleRoot.createChildDirectory(ourProjectModel, "dir" + i);
          for (int j = 0; j < 50; j++) {
            directory = directory.createChildDirectory(ourProjectModel, "subDir");
          }
          for (int j = 0; j < 50; j++) {
            ourSourceFilesToTest.add(directory.createChildData(ourProjectModel, "file" + j));
          }
        }
      });
      Module bigModule = ourProjectModel.createModule("big");
      PsiTestUtil.addSourceRoot(bigModule, bigModuleRoot);
      
      for (int i = 0; i < 500; i++) {
        Module module = ourProjectModel.createModule("small" + i);
        VirtualFile smallModuleRoot = ourProjectRoot.createChildDirectory(ourProjectModel, "small" + i);
        PsiTestUtil.addContentRoot(module, smallModuleRoot);
        VirtualFile srcRoot = smallModuleRoot.createChildDirectory(ourProjectModel, "src");
        PsiTestUtil.addSourceRoot(module, srcRoot);
        srcRoot.createChildData(ourProjectModel, "File" + i + ".java");
        VirtualFile excludedRoot = smallModuleRoot.createChildDirectory(ourProjectModel, "excluded");
        PsiTestUtil.addExcludedRoot(module, excludedRoot);

        VirtualFile libraryRoot = smallModuleRoot.createChildDirectory(ourProjectModel, "lib");
        VirtualFile libraryClassesRoot = libraryRoot.createChildDirectory(ourProjectModel, "classes");
        VirtualFile librarySourcesRoot = libraryRoot.createChildDirectory(ourProjectModel, "src");
        LibraryEx library = ourProjectModel.addProjectLevelLibrary("lib" + i, (model) -> {
          model.addRoot(libraryClassesRoot, OrderRootType.CLASSES);
          model.addRoot(librarySourcesRoot, OrderRootType.CLASSES);
          return Unit.INSTANCE;
        });
        ModuleRootModificationUtil.addDependency(module, library);
      }
    });
  }

  @AfterAll
  static void disposeProject() {
    VfsTestUtil.deleteFile(ourProjectRoot);
    ourSourceFilesToTest.clear();
  }
  
  @Test
  public void testAccessPerformance() {

    VirtualFile noId1 = new LightVirtualFile();
    VirtualFile noId2 = new LightVirtualFile() {
      @Override
      public VirtualFile getParent() {
        return noId1;
      }
    };
    VirtualFile noId3 = new LightVirtualFile() {
      @Override
      public VirtualFile getParent() {
        return noId2;
      }
    };
    VirtualFile[] filesWithoutId = {noId1, noId2, noId3};

    ProjectFileIndex index = ProjectFileIndex.getInstance(ourProjectModel.getProject());
    VirtualFile fsRoot = VirtualFileManager.getInstance().findFileByUrl("temp:///");

    PlatformTestUtil.startPerformanceTest("Directory index query", 2500, () -> {
      for (int i = 0; i < 100; i++) {
        assertFalse(index.isInContent(fsRoot));
        for (VirtualFile file : filesWithoutId) {
          assertFalse(file instanceof VirtualFileWithId);
          assertFalse(index.isInContent(file));
          assertFalse(index.isInSource(file));
          assertFalse(index.isInLibrary(file));
        }
        for (VirtualFile file : ourSourceFilesToTest) {
          assertTrue(index.isInContent(file));
          assertTrue(index.isInSource(file));
          assertFalse(index.isInLibrary(file));
        }
      }
    }).assertTiming();
  }
}
