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

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.roots.ui.configuration.projectRoot.LibrariesContainer;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
* @author nik
*/
public class GroovyLibraryDescription extends CustomLibraryDescription {
  private static final String GROOVY_FRAMEWORK_NAME = "Groovy";
  private final String myEnvVariable;
  private final Set<? extends LibraryKind> myLibraryKinds;
  private final String myFrameworkName;

  public GroovyLibraryDescription() {
    this("GROOVY_HOME", getAllGroovyKinds(), GROOVY_FRAMEWORK_NAME);
  }

  public static Set<? extends LibraryKind> getAllGroovyKinds() {
    final HashSet<LibraryKind> kinds = new HashSet<>();
    for (LibraryPresentationProvider provider : LibraryPresentationProvider.EP_NAME.getExtensions()) {
      if (provider instanceof GroovyLibraryPresentationProviderBase) {
        kinds.add(provider.getKind());
      }
    }
    return kinds;
  }

  public GroovyLibraryDescription(@NotNull String envVariable, @NotNull LibraryKind libraryKind, String frameworkName) {
    this(envVariable, Collections.singleton(libraryKind), frameworkName);
  }

  private GroovyLibraryDescription(@NotNull String envVariable, @NotNull final Set<? extends LibraryKind> libraryKinds, String frameworkName) {
    myEnvVariable = envVariable;
    myLibraryKinds = libraryKinds;
    myFrameworkName = frameworkName;
  }

  @Nullable
  public static GroovyLibraryPresentationProviderBase findManager(@NotNull VirtualFile dir) {
    final String name = dir.getName();

    final List<GroovyLibraryPresentationProviderBase> providers = ContainerUtil.findAll(LibraryPresentationProvider.EP_NAME.getExtensions(), GroovyLibraryPresentationProviderBase.class);
    for (final GroovyLibraryPresentationProviderBase provider : providers) {
      if (provider.managesName(name) && provider.isSDKHome(dir)) {
        return provider;
      }
    }

    for (final GroovyLibraryPresentationProviderBase manager : providers) {
      if (manager.isSDKHome(dir)) {
        return manager;
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Set<? extends LibraryKind> getSuitableLibraryKinds() {
    return myLibraryKinds;
  }

  @Override
  public NewLibraryConfiguration createNewLibrary(@NotNull JComponent parentComponent, VirtualFile contextDirectory) {
    VirtualFile initial = findFile(System.getenv(myEnvVariable));
    if (initial == null && GROOVY_FRAMEWORK_NAME.equals(myFrameworkName) && SystemInfo.isLinux) {
      initial = findFile("/usr/share/groovy");
    }

    final FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(VirtualFile file) {
        if (!super.isFileSelectable(file)) {
          return false;
        }
        return findManager(file) != null;
      }
    };
    descriptor.setTitle(myFrameworkName + " SDK");
    descriptor.setDescription("Choose a directory containing " + myFrameworkName + " distribution");
    final VirtualFile dir = FileChooser.chooseFile(descriptor, parentComponent, null, initial);
    if (dir == null) return null;

    final GroovyLibraryPresentationProviderBase provider = findManager(dir);
    if (provider == null) {
      return null;
    }

    final String path = dir.getPath();
    final String sdkVersion = provider.getSDKVersion(path);
    if (AbstractConfigUtils.UNDEFINED_VERSION.equals(sdkVersion)) {
      Messages.showErrorDialog(parentComponent,
                               "Looks like " + myFrameworkName + " distribution in specified path is broken. Cannot determine version.",
                               "Failed to Create Library");
      return null;
    }

    return new NewLibraryConfiguration(provider.getLibraryPrefix() + "-" + sdkVersion) {
      @Override
      public void addRoots(@NotNull LibraryEditor editor) {
        provider.fillLibrary(path, editor);
      }
    };
  }

  @Nullable
  private static VirtualFile findFile(String path) {
    if (path != null && !path.isEmpty()) {
      return LocalFileSystem.getInstance().findFileByPath(FileUtil.toSystemIndependentName(path));
    }
    return null;
  }

  @NotNull
  @Override
  public LibrariesContainer.LibraryLevel getDefaultLevel() {
    return LibrariesContainer.LibraryLevel.GLOBAL;
  }
}
