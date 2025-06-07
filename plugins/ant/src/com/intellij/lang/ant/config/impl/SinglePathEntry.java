// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.ui.CellAppearanceEx;
import com.intellij.openapi.roots.ui.FileAppearanceService;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class SinglePathEntry implements AntClasspathEntry {
  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE =
    singlePathEntry -> fromVirtualFile(singlePathEntry);

  static final @NonNls String PATH = "path";

  private File myFile;

  public SinglePathEntry(File file) {
    myFile = file;
  }

  public SinglePathEntry(final String osPath) {
    this(new File(osPath));
  }

  public void readExternal(final Element element) {
    String value = element.getAttributeValue(PATH);
    myFile = new File(PathUtil.toPresentableUrl(value));
  }

  @Override
  public void writeExternal(final Element element) {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myFile.getAbsolutePath().replace(File.separatorChar, '/'));
    element.setAttribute(PATH, url);
  }

  @Override
  public void addFilesTo(final List<? super File> files) {
    files.add(myFile);
  }

  @Override
  public CellAppearanceEx getAppearance() {
    return FileAppearanceService.getInstance().forIoFile(myFile);
  }

  private static SinglePathEntry fromVirtualFile(VirtualFile file) {
    return new SinglePathEntry(file.getPresentableUrl());
  }

  @SuppressWarnings("ClassNameSameAsAncestorName")
  public static class AddEntriesFactory extends AntClasspathEntry.AddEntriesFactory {
    public AddEntriesFactory(final JComponent parentComponent) {
      super(parentComponent, new FileChooserDescriptor(false, true, true, true, false, true), CREATE_FROM_VIRTUAL_FILE);
    }
  }
}
