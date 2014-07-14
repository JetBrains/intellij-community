/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.libraries.LibraryProperties;
import com.intellij.openapi.util.Comparing;
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
  public void loadState(GroovyLibraryProperties state) {
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
