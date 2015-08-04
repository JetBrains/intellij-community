/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.icons.AllIcons;
import com.intellij.openapi.fileChooser.FileChooserDescriptorFactory;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.roots.ui.ModifiableCellAppearanceEx;
import com.intellij.openapi.util.io.FileFilters;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class AllJarsUnderDirEntry implements AntClasspathEntry {

  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE = new Function<VirtualFile, AntClasspathEntry>() {
    public AntClasspathEntry fun(VirtualFile file) {
      return fromVirtualFile(file);
    }
  };

  @NonNls static final String DIR = "dir";

  private final File myDir;

  public AllJarsUnderDirEntry(final File dir) {
    myDir = dir;
  }

  public AllJarsUnderDirEntry(final String osPath) {
    this(new File(osPath));
  }

  public void writeExternal(final Element dataElement) {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myDir.getAbsolutePath().replace(File.separatorChar, '/'));
    dataElement.setAttribute(DIR, url);
  }

  public void addFilesTo(final List<File> files) {
    File[] children = myDir.listFiles(FileFilters.filesWithExtension("jar"));
    if (children != null) ContainerUtil.addAll(files, children);
  }

  public CellAppearanceEx getAppearance() {
    CellAppearanceEx appearance = FileAppearanceService.getInstance().forIoFile(myDir);
    if (appearance instanceof ModifiableCellAppearanceEx) {
      ((ModifiableCellAppearanceEx)appearance).setIcon(AllIcons.Nodes.JarDirectory);
    }
    return appearance;
  }

  private static AntClasspathEntry fromVirtualFile(final VirtualFile file) {
    return new AllJarsUnderDirEntry(file.getPath());
  }

  @SuppressWarnings("ClassNameSameAsAncestorName")
  public static class AddEntriesFactory extends AntClasspathEntry.AddEntriesFactory {
    public AddEntriesFactory(final JComponent parentComponent) {
      super(parentComponent, FileChooserDescriptorFactory.createMultipleFoldersDescriptor(), CREATE_FROM_VIRTUAL_FILE);
    }
  }
}
