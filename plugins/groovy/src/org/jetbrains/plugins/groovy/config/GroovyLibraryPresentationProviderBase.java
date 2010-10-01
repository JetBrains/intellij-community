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
package org.jetbrains.plugins.groovy.config;

import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.List;

/**
 * @author nik
 */
public abstract class GroovyLibraryPresentationProviderBase extends LibraryPresentationProvider<GroovyLibraryProperties> {
  public GroovyLibraryPresentationProviderBase(LibraryKind<GroovyLibraryProperties> kind) {
    super(kind);
  }

  public abstract LibraryManager getLibraryManager();

  @Override
  public Icon getIcon(GroovyLibraryProperties properties) {
    return getLibraryManager().getIcon();
  }

  @Override
  public String getDescription(@NotNull GroovyLibraryProperties properties) {
    final String version = properties.getVersion();
    return getLibraryManager().getLibraryCategoryName() + " library" + (version != null ? " of version " + version : ":");
  }

  @Override
  public GroovyLibraryProperties detect(@NotNull List<VirtualFile> classesRoots) {
    final VirtualFile[] libraryFiles = classesRoots.toArray(new VirtualFile[classesRoots.size()]);
    final LibraryManager manager = LibraryManager.findManagerFor(AbstractGroovyLibraryManager.EP_NAME.getExtensions(), libraryFiles);
    if (manager != null && acceptManager(manager)) {
      final String version = manager.getLibraryVersion(libraryFiles);
      return new GroovyLibraryProperties(version);
    }
    return null;
  }

  protected abstract boolean acceptManager(LibraryManager manager);
}
