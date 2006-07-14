package com.intellij.lang.ant.config.impl;

import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.roots.ui.util.CellAppearanceUtils;
import com.intellij.openapi.roots.ui.util.CompositeAppearance;
import com.intellij.openapi.util.Factory;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.util.Function;
import com.intellij.util.PathUtil;
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import java.io.File;
import java.util.List;

public class SinglePathEntry implements AntClasspathEntry {
  private static final Function<VirtualFile, AntClasspathEntry> CREATE_FROM_VIRTUAL_FILE =
    new Function<VirtualFile, AntClasspathEntry>() {
      public AntClasspathEntry fun(VirtualFile singlePathEntry) {
        return fromVirtulaFile(singlePathEntry);
      }
    };
  @NonNls static final String PATH = "path";
  private File myFile;

  public SinglePathEntry() {}

  public SinglePathEntry(File file) {
    myFile = file;
  }

  public SinglePathEntry(final String ospath) {
    this(new File(ospath));
  }

  public void readExternal(final Element element) throws InvalidDataException {
    String value = element.getAttributeValue(PATH);
    myFile = new File(PathUtil.toPresentableUrl(value));
  }

  public void writeExternal(final Element element) throws WriteExternalException {
    String url = VirtualFileManager.constructUrl(LocalFileSystem.PROTOCOL, myFile.getAbsolutePath().replace(File.separatorChar, '/'));
    element.setAttribute(PATH, url);
  }

  public void addFilesTo(final List<File> files) {
    files.add(myFile);
  }

  public CompositeAppearance getAppearance() {
    return CellAppearanceUtils.forFile(myFile);
  }

  public String getPresentablePath() {
    return myFile.getAbsolutePath();
  }

  public static SinglePathEntry fromVirtulaFile(VirtualFile file) {
    return new SinglePathEntry(file.getPresentableUrl());
  }

  public static class AddEntriesFactory implements Factory<List<AntClasspathEntry>> {
    private final JComponent myParentComponent;

    public AddEntriesFactory(final JComponent parentComponent) {
      myParentComponent = parentComponent;
    }

    public List<AntClasspathEntry> create() {
      VirtualFile[] files = FileChooser.chooseFiles(myParentComponent, new FileChooserDescriptor(false, true, true, true, false, true));
      if (files.length == 0) return null;
      return ContainerUtil.map(files, CREATE_FROM_VIRTUAL_FILE);
    }
  }
}

