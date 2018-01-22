// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author nik
 */
public class GroovyLibraryProperties extends LibraryProperties<GroovyLibraryProperties> {
  private final String myVersion;

  public GroovyLibraryProperties(String version) {
    myVersion = version;
  }

  @Nullable
  public String getVersion() {
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
    return obj instanceof GroovyLibraryProperties && Comparing.equal(myVersion, ((GroovyLibraryProperties)obj).myVersion);
  }

  @Override
  public int hashCode() {
    return myVersion != null ? myVersion.hashCode() : 0;
  }
}
