// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;

public class GroovyLibraryProperties extends LibraryProperties<GroovyLibraryProperties> {
  private final String myVersion;

  public GroovyLibraryProperties(String version) {
    myVersion = version;
  }

  public @Nullable String getVersion() {
    return myVersion;
  }

  @Override
  public GroovyLibraryProperties getState() {
    return null;
  }

  @Override
  public void loadState(@NotNull GroovyLibraryProperties state) {
  }

  @Override
  public boolean equals(Object obj) {
    return obj instanceof GroovyLibraryProperties && Objects.equals(myVersion, ((GroovyLibraryProperties)obj).myVersion);
  }

  @Override
  public int hashCode() {
    return myVersion != null ? myVersion.hashCode() : 0;
  }
}
