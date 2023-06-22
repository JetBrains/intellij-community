// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model.tests;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;

import static org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil.unmodifiableFileSet;
import static org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil.unmodifiablePathSet;

public class DefaultExternalTestSourceMapping implements ExternalTestSourceMapping {
  @Nullable
  private String testName;
  @Nullable
  private String testTaskPath;
  @NotNull
  private Set<File> sourceFolders = Collections.emptySet();

  @Override
  @NotNull
  public Set<String> getSourceFolders() {
    return unmodifiablePathSet(sourceFolders);
  }

  public void setSourceFolders(@NotNull Set<String> sourceFolders) {
    this.sourceFolders = unmodifiableFileSet(sourceFolders);
  }

  @NotNull
  @Override
  public String getTestName() {
    assert testName != null;
    return testName;
  }

  public void setTestName(@Nullable String testName) {
    this.testName = testName;
  }

  @Override
  @NotNull
  public String getTestTaskPath() {
    assert testTaskPath != null;
    return testTaskPath;
  }

  public void setTestTaskPath(@NotNull String testTaskPath) {
    this.testTaskPath = testTaskPath;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DefaultExternalTestSourceMapping mapping = (DefaultExternalTestSourceMapping)o;

    if (!Objects.equals(testName, mapping.testName)) return false;
    if (!Objects.equals(testTaskPath, mapping.testTaskPath)) return false;
    if (!sourceFolders.equals(mapping.sourceFolders)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = testName != null ? testName.hashCode() : 0;
    result = 31 * result + (testTaskPath != null ? testTaskPath.hashCode() : 0);
    result = 31 * result + sourceFolders.hashCode();
    return result;
  }
}
