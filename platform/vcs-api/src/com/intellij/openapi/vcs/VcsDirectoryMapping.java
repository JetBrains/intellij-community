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

package com.intellij.openapi.vcs;

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.SystemIndependent;

import java.util.Objects;

/**
 * @author yole
 */
public class VcsDirectoryMapping {
  public static final String DEFAULT_MAPPING_DIR = "";

  public static final String PROJECT_CONSTANT = "<Project>";
  public static final VcsDirectoryMapping[] EMPTY_ARRAY = new VcsDirectoryMapping[0];

  @NotNull private final String myDirectory;
  private final String myVcs;
  private VcsRootSettings myRootSettings;

  /**
   * Empty string as 'directory' denotes "default mapping" aka "&lt;Project&gt;".
   * Such mapping will use {@link com.intellij.openapi.vcs.impl.DefaultVcsRootPolicy} to
   * find actual vcs roots that cover project files.
   */
  public VcsDirectoryMapping(@NotNull String directory, @Nullable String vcs) {
    this(directory, vcs, null);
  }

  public VcsDirectoryMapping(@NotNull String directory, @Nullable String vcs, @Nullable VcsRootSettings rootSettings) {
    myDirectory = FileUtil.normalize(directory);
    myVcs = StringUtil.notNullize(vcs);
    myRootSettings = rootSettings;
  }

  @NotNull
  public static VcsDirectoryMapping createDefault(@NotNull String vcs) {
    return new VcsDirectoryMapping(DEFAULT_MAPPING_DIR, vcs);
  }

  @NotNull
  @SystemIndependent
  public String getDirectory() {
    return myDirectory;
  }

  /**
   * @deprecated Use {@link #getDirectory()}
   */
  @NotNull
  @Deprecated
  public String systemIndependentPath() {
    return myDirectory;
  }

  @NotNull
  public String getVcs() {
    return myVcs;
  }

  /**
   * Returns the VCS-specific settings for the given mapping.
   *
   * @return VCS-specific settings, or null if none have been defined.
   * @see AbstractVcs#getRootConfigurable(VcsDirectoryMapping)
   */
  @Nullable
  public VcsRootSettings getRootSettings() {
    return myRootSettings;
  }

  /**
   * Sets the VCS-specific settings for the given mapping.
   *
   * @param rootSettings the VCS-specific settings
   * @deprecated Use constructor parameter
   */
  @Deprecated
  public void setRootSettings(final VcsRootSettings rootSettings) {
    myRootSettings = rootSettings;
  }

  /**
   * @return if this mapping denotes "default mapping" aka "&lt;Project&gt;".
   */
  public boolean isDefaultMapping() {
    return myDirectory.length() == 0;
  }

  /**
   * @return if this mapping denotes "no vcs" aka "&lt;none&gt;".
   */
  public boolean isNoneMapping() {
    return myVcs.isEmpty();
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final VcsDirectoryMapping mapping = (VcsDirectoryMapping)o;

    if (!myDirectory.equals(mapping.myDirectory)) return false;
    if (!Objects.equals(myVcs, mapping.myVcs)) return false;
    if (!Objects.equals(myRootSettings, mapping.myRootSettings)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myDirectory.hashCode();
    result = 31 * result + (myVcs != null ? myVcs.hashCode() : 0);
    return result;
  }

  @Override
  public String toString() {
    return isDefaultMapping() ? PROJECT_CONSTANT : myDirectory;
  }
}