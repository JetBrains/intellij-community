/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

package com.intellij.openapi.projectRoots.ex;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

public interface ProjectRootContainer {
  @NotNull
  VirtualFile[] getRootFiles(@NotNull OrderRootType type);
  @NotNull ProjectRoot[] getRoots(@NotNull OrderRootType type);

  // must execute modifications inside this method only
  void changeRoots(@NotNull Runnable change);

  @NotNull 
  ProjectRoot addRoot(@NotNull VirtualFile virtualFile, @NotNull OrderRootType type);
  void addRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type);
  void removeRoot(@NotNull ProjectRoot root, @NotNull OrderRootType type);
  void removeAllRoots(@NotNull OrderRootType type);

  void removeAllRoots();

  void removeRoot(@NotNull VirtualFile root, @NotNull OrderRootType type);

  void update();
}
