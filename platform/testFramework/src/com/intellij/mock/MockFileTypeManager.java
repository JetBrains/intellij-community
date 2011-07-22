package com.intellij.mock;

import com.intellij.lang.Language;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.fileTypes.ex.FileTypeManagerEx;
import com.intellij.openapi.fileTypes.impl.AbstractFileType;
import com.intellij.openapi.options.SchemesManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ArrayUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.List;

public class MockFileTypeManager extends FileTypeManagerEx {

  private FileType fileType;

  public MockFileTypeManager(FileType fileType) {
    this.fileType = fileType;
  }

  @Override
  public void registerFileType(FileType fileType) {
  }

  @Override
  public void unregisterFileType(FileType fileType) {
  }

  @Override
  @NotNull
  public String getIgnoredFilesList() {
    throw new IncorrectOperationException();
  }

  @Override
  public void setIgnoredFilesList(@NotNull String list) {
  }

  @Override
  public boolean isIgnoredFilesListEqualToCurrent(String list) {
    return false;
  }

  public void save() {
  }

  @Override
  @NotNull
  public String getExtension(String fileName) {
    return "";
  }

  @Override
  public void registerFileType(@NotNull FileType type, @NotNull List<FileNameMatcher> defaultAssociations) {
  }

  @Override
  public void fireFileTypesChanged() {
  }

  @Override
  @NotNull
  public FileType getFileTypeByFileName(@NotNull String fileName) {
    return fileType;
  }

  @Override
  @NotNull
  public FileType getFileTypeByFile(@NotNull VirtualFile file) {
    return fileType;
  }

  @Override
  @NotNull
  public FileType getFileTypeByExtension(@NotNull String extension) {
    return fileType;
  }

  @Override
  @NotNull
  public FileType[] getRegisteredFileTypes() {
    return FileType.EMPTY_ARRAY;
  }

  @Override
  public boolean isFileIgnored(@NotNull String name) {
    return false;
  }

  @Override
  public boolean isFileIgnored(@NonNls @NotNull VirtualFile file) {
    return false;
  }

  @Override
  @NotNull
  public String[] getAssociatedExtensions(@NotNull FileType type) {
    return ArrayUtil.EMPTY_STRING_ARRAY;
  }

  @Override
  public void fireBeforeFileTypesChanged() {
  }

  @Override
  public void addFileTypeListener(@NotNull FileTypeListener listener) {
  }

  @Override
  public void removeFileTypeListener(@NotNull FileTypeListener listener) {
  }

  @Override
  public FileType getKnownFileTypeOrAssociate(@NotNull VirtualFile file) {
    return file.getFileType();
  }

  @Override
  @NotNull
  public List<FileNameMatcher> getAssociations(@NotNull FileType type) {
    return Collections.emptyList();
  }

  @Override
  public void associate(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
  }

  @Override
  public void removeAssociation(@NotNull FileType type, @NotNull FileNameMatcher matcher) {
  }

  @Override
  @NotNull
  public FileType getStdFileType(@NotNull @NonNls final String fileTypeName) {
    if ("ARCHIVE".equals(fileTypeName) || "CLASS".equals(fileTypeName)) return UnknownFileType.INSTANCE;
    if ("PLAIN_TEXT".equals(fileTypeName)) return PlainTextFileType.INSTANCE;
    Language language = Language.findLanguageByID(fileTypeName);
    if (language == null) {
      // StdFileTypes initialization..
      if ("JAVA".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.java.JavaLanguage");
      if ("XML".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.xml.XMLLanguage");
      if ("DTD".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.dtd.DTDLanguage");
      if ("JSP".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.jsp.NewJspLanguage");
      if ("JSPX".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.jspx.JSPXLanguage");
      if ("HTML".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.html.HTMLLanguage");
      if ("XHTML".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.xhtml.XHTMLLanguage");
      if ("JavaScript".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.javascript.JavascriptLanguage");
      if ("Properties".equals(fileTypeName)) language = loadLanguageSafe("com.intellij.lang.properties.PropertiesLanguage");
    }
    return new MockLanguageFileType(language == null? PlainTextLanguage.INSTANCE : language, fileTypeName.toLowerCase());
  }

  private static Language loadLanguageSafe(final String className) {
    try {
      return (Language)Class.forName(className).getField("INSTANCE").get(null);
    }
    catch (Exception e) {
      return null;
    }
  }

  @Override
  public SchemesManager<FileType, AbstractFileType> getSchemesManager() {
    return SchemesManager.EMPTY;
  }

  @Override
  public boolean isFileOfType(VirtualFile file, FileType type) {
   return false;
  }
}
