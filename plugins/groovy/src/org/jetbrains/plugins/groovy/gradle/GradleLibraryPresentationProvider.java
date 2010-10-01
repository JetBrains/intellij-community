/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gradle;

import com.intellij.openapi.roots.libraries.LibraryKind;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;
import org.jetbrains.plugins.groovy.config.GroovyLibraryProperties;
import org.jetbrains.plugins.groovy.config.LibraryManager;

/**
 * @author nik
 */
public class GradleLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  private static final LibraryKind<GroovyLibraryProperties> GRADLE_KIND = LibraryKind.create("gradle");
  private GradleLibraryManager myLibraryManager = new GradleLibraryManager();

  public GradleLibraryPresentationProvider() {
    super(GRADLE_KIND);
  }

  @Override
  public LibraryManager getLibraryManager() {
    return myLibraryManager;
  }

  @Override
  protected boolean acceptManager(LibraryManager manager) {
    return manager instanceof GradleLibraryManager;
  }
}
