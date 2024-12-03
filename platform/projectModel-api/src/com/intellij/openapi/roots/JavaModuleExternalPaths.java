// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class JavaModuleExternalPaths extends ModuleExtension {
  public static JavaModuleExternalPaths getInstance(@NotNull Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(JavaModuleExternalPaths.class);
  }

  public abstract VirtualFile @NotNull [] getExternalAnnotationsRoots();

  public abstract String @NotNull [] getExternalAnnotationsUrls();

  public abstract void setExternalAnnotationUrls(String @NotNull [] urls);


  public abstract String @NotNull [] getJavadocUrls();

  public abstract void setJavadocUrls(String @NotNull [] urls);
}
