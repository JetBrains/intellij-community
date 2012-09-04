/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.LibraryKind;
import com.intellij.openapi.roots.ui.configuration.libraryEditor.LibraryEditor;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import icons.JetgroovyIcons;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyLibraryPresentationProviderBase;

import javax.swing.*;
import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author nik
 */
public class GppLibraryPresentationProvider extends GroovyLibraryPresentationProviderBase {
  private static final LibraryKind GPP_KIND = LibraryKind.create("gpp");
  private static final Pattern GROOVYPP_JAR = Pattern.compile("groovypp-([\\d\\.]+)\\.jar");
  private static final Pattern GROOVYPP_ALL_JAR = Pattern.compile("groovypp-all-([\\d\\.]+)\\.jar");

  public GppLibraryPresentationProvider() {
    super(GPP_KIND);
  }

  @Override
  protected void fillLibrary(String path, LibraryEditor libraryEditor) {
    File lib = new File(path + "/lib");
    if (lib.exists()) {
      libraryEditor.addJarDirectory(VfsUtil.getUrlForLibraryRoot(lib), false, OrderRootType.CLASSES);
    }

    File srcRoot = new File(path + "/src");
    addSources(libraryEditor, srcRoot.exists() ? srcRoot : new File(path));
  }

  private static void addSources(LibraryEditor libraryEditor, File srcRoot) {
    File compilerSrc = new File(srcRoot, "Compiler/src");
    if (compilerSrc.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(compilerSrc), OrderRootType.SOURCES);
    }

    File stdLibSrc = new File(srcRoot, "StdLib/src");
    if (stdLibSrc.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(stdLibSrc), OrderRootType.SOURCES);
    }

    File mainSrc = new File(srcRoot, "main");
    if (mainSrc.exists()) {
      libraryEditor.addRoot(VfsUtil.getUrlForLibraryRoot(mainSrc), OrderRootType.SOURCES);
    }
  }

  @Override
  public boolean managesLibrary(final VirtualFile[] libraryFiles) {
    return getGppVersion(libraryFiles) != null;
  }

  @Nls
  @Override
  public String getLibraryVersion(final VirtualFile[] libraryFiles) {
    return getGppVersion(libraryFiles);
  }

  @Nullable
  private static String getGppVersion(VirtualFile[] files) {
    for (VirtualFile file : files) {
      Matcher matcher = GROOVYPP_JAR.matcher(file.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }

      matcher = GROOVYPP_ALL_JAR.matcher(file.getName());
      if (matcher.matches()) {
        return matcher.group(1);
      }
    }
    return null;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    return JetgroovyIcons.Groovy.Groovy_16x16;
  }

  @NotNull
  @Override
  public String getSDKVersion(String path) {
    final VirtualFile file = LocalFileSystem.getInstance().findFileByPath(path);
    assert file != null;
    final VirtualFile libDir = file.findChild("lib");
    assert libDir != null;
    final String version = getGppVersion(libDir.getChildren());
    if (version != null) {
      return version;
    }
    throw new AssertionError(path);
  }


  @Nls
  @NotNull
  @Override
  public String getLibraryCategoryName() {
    return "Groovy++";
  }

  public boolean managesName(@NotNull String name) {
    return super.managesName(name) || StringUtil.startsWithIgnoreCase(name, "groovypp");
  }

  @Override
  public boolean isSDKHome(@NotNull VirtualFile file) {
    final VirtualFile libDir = file.findChild("lib");
    return libDir != null && getGppVersion(libDir.getChildren()) != null;
  }
}
