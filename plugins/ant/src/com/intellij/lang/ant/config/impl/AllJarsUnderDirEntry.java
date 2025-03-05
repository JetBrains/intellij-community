// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE = file -> fromVirtualFile(file);

  static final @NonNls String DIR = "dir";

  private final File myDir;

  public AllJarsUnderDirEntry(final File dir) {
    myDir = dir;
  }

  public AllJarsUnderDirEntry(final String osPath) {
    this(new File(osPath));
  }

  @Override
  public void writeExternal(final Element dataElement) {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myDir.getAbsolutePath().replace(File.separatorChar, '/'));
    dataElement.setAttribute(DIR, url);
  }

  @Override
  public void addFilesTo(final List<? super File> files) {
    File[] children = myDir.listFiles(FileFilters.filesWithExtension("jar"));
    if (children != null) ContainerUtil.addAll(files, children);
  }

  @Override
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
