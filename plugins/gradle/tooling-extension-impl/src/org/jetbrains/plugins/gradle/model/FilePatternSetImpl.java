// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.model;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class FilePatternSetImpl implements FilePatternSet {
  private Set<String> includes;
  private Set<String> excludes;

  public FilePatternSetImpl(Set<String> includes, Set<String> excludes) {
    this.includes = new LinkedHashSet<>(includes);
    this.excludes = new LinkedHashSet<>(excludes);
  }

  public FilePatternSetImpl() {
    includes = new LinkedHashSet<>(0);
    excludes = new LinkedHashSet<>(0);
  }

  @Override
  public Set<String> getIncludes() {
    return includes;
  }

  public void setIncludes(Set<String> includes) {
    this.includes = includes;
  }

  @Override
  public Set<String> getExcludes() {
    return excludes;
  }

  public void setExcludes(Set<String> excludes) {
    this.excludes = excludes;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    FilePatternSetImpl set = (FilePatternSetImpl)o;

    if (!Objects.equals(includes, set.includes)) return false;
    if (!Objects.equals(excludes, set.excludes)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = includes != null ? includes.hashCode() : 0;
    result = 31 * result + (excludes != null ? excludes.hashCode() : 0);
    return result;
  }
}
