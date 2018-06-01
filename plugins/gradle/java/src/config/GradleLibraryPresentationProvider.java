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
package org.jetbrains.plugins.gradle.config;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.GradleIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.service.GradleInstallationManager;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;
import org.jetbrains.plugins.groovy.config.GroovyLibraryProperties;

import javax.swing.*;
import java.io.File;
import java.util.regex.Matcher;

/**
 * @author nik
 */
public class GradleLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {

  private static final LibraryKind GRADLE_KIND = LibraryKind.create(GradleConstants.EXTENSION);

  private final GradleInstallationManager myLibraryManager;

  public GradleLibraryPresentationProvider(@NotNull GradleInstallationManager libraryManager) {
    super(GRADLE_KIND);
    myLibraryManager = libraryManager;
  }

  @NotNull
  @Override
  public Icon getIcon(GroovyLibraryProperties properties) {
    return GradleIcons.Gradle;
  }

  @Nls
  @Override
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    return getGradleVersion(libraryFiles);
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    return myLibraryManager.isGradleSdkHome(file);
  }

  @Override
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return myLibraryManager.isGradleSdk(libraryFiles);
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    assert file != null;
    VirtualFile lib = file.findChild("lib");
    assert lib != null;
    for (VirtualFile virtualFile : lib.getChildren()) {
      final String version = getGradleJarVersion(virtualFile);
      if (version != null) {
        return version;
      }
    }
    throw new AssertionError(path);
  }

  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Gradle";
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File lib = new File(path + "/lib");
    File[] jars = lib.exists() ? lib.listFiles() : new File[0];
    if (jars != null) {
      for (File file : jars) {
        if (file.getName().endsWith(".jar")) {
          libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(file), OrderRootType.CLASSES);
        }
      }
    }
  }

  @Nullable
  private static String getGradleVersion(VirtualFile[] libraryFiles) {
    for (VirtualFile file : libraryFiles) {
      final String version = getGradleJarVersion(file);
      if (version != null) {
        return version;
      }
    }
    return null;
  }

  @Nullable
  private static String getGradleJarVersion(VirtualFile file) {
    final Matcher matcher = GradleInstallationManager.GRADLE_JAR_FILE_PATTERN.matcher(file.getName());
    if (matcher.matches()) {
      return matcher.group(2);
    }
    return null;
  }
}
