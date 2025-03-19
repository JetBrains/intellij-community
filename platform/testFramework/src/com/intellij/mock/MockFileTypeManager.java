// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.mock;

import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class MockFileTypeManager extends FileTypeManagerEx {
  private final FileType fileType;

  public MockFileTypeManager(FileType fileType) {
    this.fileType = fileType;
  }

  @Override
  public void freezeFileTypeTemporarilyIn(@NotNull VirtualFile file, @NotNull Runnable runnable) { }

  @Override
  public @NotNull String getIgnoredFilesList() {
    throw new IncorrectOperationException();
  }

  @Override
  public void setIgnoredFilesList(@NotNull String list) { }

  @Override
  public boolean isIgnoredFilesListEqualToCurrent(@NotNull String list) {
    return false;
  }

  public void save() { }

  @Override
  public @NotNull String getExtension(@NotNull String fileName) {
    return "";
  }

  @Override
  public void fireFileTypesChanged() { }

  @Override
  public @NotNull FileType getFileTypeByFileName(@NotNull String fileName) {
    return fileType;
  }

  @Override
  public @NotNull FileType getFileTypeByFile(@NotNull VirtualFile file) {
    return fileType;
  }

  @Override
  public @NotNull FileType getFileTypeByExtension(@NotNull String extension) {
    return fileType;
  }

  @Override
  public FileType @NotNull [] getRegisteredFileTypes() {
    return FileType.EMPTY_ARRAY;
  }

  @Override
  public boolean isFileIgnored(@NotNull String name) {
    return false;
  }

  @Override
  public boolean isFileIgnored(@NotNull VirtualFile file) {
    return false;
  }

  @Override
  public void fireBeforeFileTypesChanged() { }

  @Override
  public void makeFileTypesChange(@NotNull String message, @NotNull Runnable command) {
    command.run();
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file, @NotNull Project project) {
    return file.getFileType();
  }

  @Override
  public @NotNull List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return Collections.emptyList();
  }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) { }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) { }

  @Override
  public @NotNull FileType getStdFileType(@NotNull String fileTypeName) {
    if ("ARCHIVE".equals(fileTypeName)) return UnknownFileType.INSTANCE;
    if ("PLAIN_TEXT".equals(fileTypeName)) return PlainTextFileType.INSTANCE;
    if ("CLASS".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.JavaClassFileType", fileTypeName);
    if ("JAVA".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.JavaFileType", fileTypeName);
    if ("XML".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.XmlFileType", fileTypeName);
    if ("DTD".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.DTDFileType", fileTypeName);
    if ("JSP".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.NewJspFileType", fileTypeName);
    if ("JSPX".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.JspxFileType", fileTypeName);
    if ("HTML".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.HtmlFileType", fileTypeName);
    if ("XHTML".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.ide.highlighter.XHtmlFileType", fileTypeName);
    if ("JavaScript".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.lang.javascript.JavaScriptFileType", fileTypeName);
    if ("Properties".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.lang.properties.PropertiesFileType", fileTypeName);
    if ("GUI_DESIGNER_FORM".equals(fileTypeName)) return loadFileTypeSafe("com.intellij.uiDesigner.GuiFormFileType", fileTypeName);
    return new MockLanguageFileType(PlainTextLanguage.INSTANCE, fileTypeName.toLowerCase(Locale.ENGLISH));
  }

  private static FileType loadFileTypeSafe(String className, String fileTypeName) {
    try {
      return (FileType)Class.forName(className).getField("INSTANCE").get(null);
    }
    catch (Exception ignored) {
      return new MockLanguageFileType(PlainTextLanguage.INSTANCE, fileTypeName.toLowerCase(Locale.ENGLISH));
    }
  }

  @Override
  public @Nullable FileType findFileTypeByName(@NotNull String fileTypeName) {
    return null;
  }
}
