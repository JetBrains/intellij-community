/*
 * Copyright (C) 2017 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.api;

import com.android.tools.idea.gradle.dsl.api.util.GradleDslModel;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface GradleFileModel extends GradleDslModel {
  @NotNull
  Project getProject();

  void reparse();

  boolean isModified();

  void resetState();

  @NotNull
  VirtualFile getVirtualFile();

  void applyChanges();

  @NotNull
  Map<String, List<BuildModelNotification>> getNotifications();

  /**
   * @return the psi file representing this GradleFileModel. In order to continue using this instance of the model no modifications should
   * be made to the underlying psi tree of the file for the models lifetime. This method is exposed to allow the PsiFile to be passed into
   * Intellij IDEA APIs. This method makes no guarantees about the validity of the returned element, callers should perform the necessary
   * checks before using.
   */
  @Nullable
  PsiFile getPsiFile();
}
