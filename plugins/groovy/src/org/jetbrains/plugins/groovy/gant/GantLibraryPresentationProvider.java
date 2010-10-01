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
package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.roots.libraries.LibraryKind;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;
import org.jetbrains.plugins.groovy.config.GroovyLibraryProperties;
import org.jetbrains.plugins.groovy.config.LibraryManager;

/**
 * @author nik
 */
public class GantLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  private static final LibraryKind<GroovyLibraryProperties> GANT_KIND = LibraryKind.create("gant");
  private GantLibraryManager myLibraryManager = new GantLibraryManager();

  public GantLibraryPresentationProvider() {
    super(GANT_KIND);
  }

  @Override
  public LibraryManager getLibraryManager() {
    return myLibraryManager;
  }

  @Override
  protected boolean acceptManager(LibraryManager manager) {
    return manager instanceof GantLibraryManager;
  }
}
