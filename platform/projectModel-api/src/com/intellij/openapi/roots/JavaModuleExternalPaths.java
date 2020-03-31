/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.roots;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

@ApiStatus.NonExtendable
public abstract class JavaModuleExternalPaths extends ModuleExtension {

  public static JavaModuleExternalPaths getInstance(Module module) {
    return ModuleRootManager.getInstance(module).getModuleExtension(JavaModuleExternalPaths.class);
  }

  public abstract VirtualFile @NotNull [] getExternalAnnotationsRoots();

  public abstract String @NotNull [] getExternalAnnotationsUrls();

  public abstract void setExternalAnnotationUrls(String @NotNull [] urls);


  public abstract String @NotNull [] getJavadocUrls();

  public abstract void setJavadocUrls(String @NotNull [] urls);


}
