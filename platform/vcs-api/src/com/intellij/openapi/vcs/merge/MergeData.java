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

import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MergeData contains information about revisions
 *
 * ORIGINAL - is the content which the file had before conflicting change  ("Base" - middle panel)
 * LAST     - is the content which came from the server while updating     ("Theirs" - right panel)
 * CURRENT  - is the content from local changes                            ("Ours" - left panel)
 *
 * byte[] - raw file content
 * {@link MergeProvider} should initialize all three contents, because null value is treated as an error, not as blank content.
 *
 * @author lesya
 */
public class MergeData {
  @NotNull public byte[] ORIGINAL;
  @NotNull public byte[] LAST;
  @NotNull public byte[] CURRENT;

  @Nullable public VcsRevisionNumber ORIGINAL_REVISION_NUMBER;
  @Nullable public VcsRevisionNumber LAST_REVISION_NUMBER;
  @Nullable public VcsRevisionNumber CURRENT_REVISION_NUMBER;

  @Nullable public FilePath ORIGINAL_FILE_PATH;
  @Nullable public FilePath LAST_FILE_PATH;
  @Nullable public FilePath CURRENT_FILE_PATH;
}
