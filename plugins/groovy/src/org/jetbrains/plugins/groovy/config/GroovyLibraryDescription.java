// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.config;

import com.intellij.framework.library.DownloadableLibraryType;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.libraries.LibraryPresentationProvider;
import com.intellij.openapi.roots.libraries.LibraryType;
import com.intellij.openapi.roots.libraries.NewLibraryConfiguration;
import com.intellij.openapi.roots.ui.configuration.libraries.CustomLibraryDescription;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyBundle;

import javax.swing.*;
import java.awt.*;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class GroovyLibraryDescription extends CustomLibraryDescription {
  private static final String GROOVY_FRAMEWORK_NAME = "Groovy";
  private final String myEnvVariable;
  private final Set<? extends LibraryKind> myLibraryKinds;
  private final String myFrameworkName;
  private final DownloadableLibraryType myLibraryType;

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
    myLibraryType = LibraryType.EP_NAME.findExtension(GroovyDownloadableLibraryType.class);
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
    VirtualFile initial = findPathToGroovyHome();

    final FileChooserDescriptor descriptor = createFileChooserDescriptor();
    final VirtualFile dir = FileChooser.chooseFile(descriptor, parentComponent, null, initial);
    if (dir == null) {
      return null;
    }
    return createLibraryConfiguration(parentComponent, dir);
  }

  @Nullable
  public VirtualFile findPathToGroovyHome() {
    VirtualFile initial = findFile(System.getenv(myEnvVariable));
    if (initial == null && GROOVY_FRAMEWORK_NAME.equals(myFrameworkName)) {
      if (SystemInfo.isLinux) {
        return findFile("/usr/share/groovy");
      }
      else if (SystemInfo.isMac) {
        return findFile("/usr/local/opt/groovy/libexec"); // homebrew
      }
    }
    return initial;
  }

  @NotNull
  public FileChooserDescriptor createFileChooserDescriptor() {
    FileChooserDescriptor descriptor = new FileChooserDescriptor(false, true, false, false, false, false) {
      @Override
      public boolean isFileSelectable(@Nullable VirtualFile file) {
        if (!super.isFileSelectable(file)) {
          return false;
        }
        return findManager(file) != null;
      }
    };
    descriptor.setTitle(GroovyBundle.message("framework.0.sdk.chooser.title", myFrameworkName));
    descriptor.setDescription(GroovyBundle.message("framework.0.sdk.chooser.description", myFrameworkName));
    return descriptor;
  }

  public @Nullable NewLibraryConfiguration createLibraryConfiguration(@Nullable Component parentComponent, @NotNull VirtualFile pathToLibrary) {
    final GroovyLibraryPresentationProviderBase provider = findManager(pathToLibrary);
    if (provider == null) {
      return null;
    }

    final String path = pathToLibrary.getPath();
    final String sdkVersion = provider.getSDKVersion(path);
    if (sdkVersion == null) {
      Messages.showErrorDialog(
        parentComponent,
        GroovyBundle.message("framework.0.sdk.chooser.error.message", myFrameworkName),
        GroovyBundle.message("framework.0.sdk.chooser.error.title")
      );
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

  @Override
  public @Nullable DownloadableLibraryType getDownloadableLibraryType() {
    return myLibraryType;
  }
}
