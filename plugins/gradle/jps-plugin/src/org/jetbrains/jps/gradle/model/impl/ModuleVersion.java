// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.gradle.model.impl;

import com.dynatrace.hash4j.hashing.HashSink;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Vladislav.Soroka
 */
public final class ModuleVersion {
  @Tag("groupId")
  public String groupId;

  @Tag("artifactId")
  public String artifactId;

  @Tag("version")
  public String version;

  public ModuleVersion() {
  }

  public ModuleVersion(String groupId, String artifactId, String version) {
    this.groupId = groupId;
    this.artifactId = artifactId;
    this.version = version;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ModuleVersion bean = (ModuleVersion)o;

    if (artifactId != null ? !artifactId.equals(bean.artifactId) : bean.artifactId != null) return false;
    if (groupId != null ? !groupId.equals(bean.groupId) : bean.groupId != null) return false;
    if (version != null ? !version.equals(bean.version) : bean.version != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = groupId != null ? groupId.hashCode() : 0;
    result = 31 * result + (artifactId != null ? artifactId.hashCode() : 0);
    result = 31 * result + (version != null ? version.hashCode() : 0);
    return result;
  }

  public void computeHash(@NotNull HashSink hash) {
    hashNullableString(groupId, hash);
    hashNullableString(artifactId, hash);
    hashNullableString(version, hash);
  }

  private static void hashNullableString(@Nullable String s, @NotNull HashSink hash) {
    if (s == null) {
      hash.putInt(-1);
    }
    else {
      hash.putString(s);
    }
  }
}
