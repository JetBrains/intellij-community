// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing.roots;

import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.testFramework.HeavyPlatformTestCase;
import com.intellij.testFramework.PsiTestUtil;
import com.intellij.testFramework.TestModeFlags;
import com.intellij.util.indexing.IndexableFilesIndex;
import org.jetbrains.annotations.NotNull;

public abstract class IndexableFilesIndexTestCase extends HeavyPlatformTestCase {
  protected IndexableFilesIndex myIndex;
  private Boolean oldFlagValue;

  @Override
  protected void setUp() throws Exception {
    super.setUp();
    oldFlagValue = TestModeFlags.get(IndexableFilesIndex.ENABLE_IN_TESTS);
    TestModeFlags.set(IndexableFilesIndex.ENABLE_IN_TESTS, true);
    myIndex = IndexableFilesIndex.getInstance(getProject());
  }

  @Override
  protected void tearDown() throws Exception {
    myIndex = null;
    try {
      TestModeFlags.set(IndexableFilesIndex.ENABLE_IN_TESTS, oldFlagValue);
      oldFlagValue = null;
    }
    catch (Throwable e) {
      addSuppressedException(e);
    }
    finally {
      super.tearDown();
    }
  }

  protected void assertNotIndexed(VirtualFile file) {
    assertFalse("Shouldn't be indexed: " + file.getPath(), myIndex.shouldBeIndexed(file));
  }

  protected void assertIndexed(VirtualFile file) {
    assertTrue("Expected to be indexed but is not: " + file.getPath(), myIndex.shouldBeIndexed(file));
  }

  @NotNull
  protected static Module createJavaModuleWithContent(@NotNull Project project, @NotNull String name, @NotNull VirtualFile contentRoot) {
    ModuleType<?> type = ModuleTypeManager.getInstance().findByID(ModuleTypeId.JAVA_MODULE);
    return WriteCommandAction.writeCommandAction(project).compute(() -> {
      ModifiableModuleModel moduleModel = ModuleManager.getInstance(project).getModifiableModel();
      Module module = moduleModel.newModule(contentRoot.toNioPath().resolve(name + ".iml"), type.getId());
      moduleModel.commit();
      assertNotNull(module);
      PsiTestUtil.addContentRoot(module, contentRoot);
      return module;
    });
  }
}
