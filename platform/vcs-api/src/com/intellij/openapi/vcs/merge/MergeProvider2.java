/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.merge;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * Merge provider which allows plugging into the functionality of the Multiple File Merge dialog.
 *
 * @author yole
 * @since 8.1
 */
public interface MergeProvider2 extends MergeProvider {


  /**
   * Initiates a multiple file merge operation for the specified list of files.
   *
   * @param files the list of files to be merged.
   * @return the merge session instance.
   */
  @NotNull
  MergeSession createMergeSession(@NotNull List<VirtualFile> files);

}
