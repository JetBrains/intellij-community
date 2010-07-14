/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.io.FileFilter;
import java.util.List;

public class AllJarsUnderDirEntry implements AntClasspathEntry {
  private static final Icon ALL_JARS_IN_DIR_ICON = IconLoader.getIcon("/ant/allJarsInDir.png");
  private final File myDir;
  @NonNls static final String DIR = "dir";
  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE = new Function<VirtualFile, AntClasspathEntry>() {
    public AntClasspathEntry fun(VirtualFile file) {
      return fromVirtualFile(file);
    }
  };
  @NonNls public static final String JAR_SUFFIX = ".jar";

  public AllJarsUnderDirEntry(final File dir) {
    myDir = dir;
  }

  public AllJarsUnderDirEntry(final String osPath) {
    this(new File(osPath));
  }

  public String getPresentablePath() {
    return myDir.getAbsolutePath();
  }

  public void writeExternal(final Element dataElement) throws WriteExternalException {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myDir.getAbsolutePath().replace(File.separatorChar, '/'));
    dataElement.setAttribute(DIR, url);
  }

  public void addFilesTo(final List<File> files) {
    File[] children = myDir.listFiles(new FileFilter() {
      public boolean accept(File pathname) {
        return pathname.getName().endsWith(JAR_SUFFIX) && pathname.isFile();
      }
    });
    if (children != null) ContainerUtil.addAll(files, children);
  }

  public CompositeAppearance getAppearance() {
    CompositeAppearance appearance = CellAppearanceUtils.forFile(myDir);
    appearance.setIcon(ALL_JARS_IN_DIR_ICON);
    return appearance;
  }

  private static AntClasspathEntry fromVirtualFile(final VirtualFile file) {
    return new AllJarsUnderDirEntry(file.getPath());
  }

  public static class AddEntriesFactory implements Factory<List<AntClasspathEntry>> {
    private final JComponent myParentComponent;

    public AddEntriesFactory(final JComponent component) {
      myParentComponent = component;
    }

    public List<AntClasspathEntry> create() {
      VirtualFile[] files = FileChooser.chooseFiles(myParentComponent, new FileChooserDescriptor(false, true, false, false, false, true));
      if (files.length == 0) return null;
      return ContainerUtil.map(files, CREATE_FROM_VIRTUAL_FILE);
    }
  }
}
