// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.pointers.VirtualFilePointer;
import org.jetbrains.annotations.Nullable;

public abstract class CompilerModuleExtension extends ModuleExtension {
  public static final String PRODUCTION = "production";
  public static final String TEST = "test";

  @Nullable
  public static CompilerModuleExtension getInstance(final Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(CompilerModuleExtension.class);
  }

  /**
   * Returns a compiler output path for production sources of the module, if it is valid.
   */
  @Nullable
  public abstract VirtualFile getCompilerOutputPath();

  public abstract void setCompilerOutputPath(VirtualFile file);

  /**
   * Returns a compiler output path URL for production sources of the module.
   */
  @Nullable
  public abstract String getCompilerOutputUrl();

  public abstract void setCompilerOutputPath(String url);

  /**
   * Returns a compiler output path for test sources of the module, if it is valid.
   */
  @Nullable
  public abstract VirtualFile getCompilerOutputPathForTests();

  public abstract void setCompilerOutputPathForTests(VirtualFile file);

  /**
   * Returns a compiler output path URL for test sources of the module.
   */
  @Nullable
  public abstract String getCompilerOutputUrlForTests();

  public abstract void setCompilerOutputPathForTests(String url);

  /**
   * Makes this module inheriting compiler output from its project
   *
   * @param inherit whether or not compiler output is inherited
   */
  public abstract void inheritCompilerOutputPath(boolean inherit);

  /**
   * Returns {@code true} if compiler output for this module is inherited from a project
   */
  public abstract boolean isCompilerOutputPathInherited();

  public abstract VirtualFilePointer getCompilerOutputPointer();

  public abstract VirtualFilePointer getCompilerOutputForTestsPointer();

  public abstract void setExcludeOutput(boolean exclude);

  public abstract boolean isExcludeOutput();

  public abstract VirtualFile[] getOutputRoots(boolean includeTests);

  public abstract String[] getOutputRootUrls(boolean includeTests);
}