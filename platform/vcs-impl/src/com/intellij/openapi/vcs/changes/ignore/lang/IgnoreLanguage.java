/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 hsz Jakub Chrzanowski <jakub@hsz.mobi>
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package com.intellij.openapi.vcs.changes.ignore.lang;

import com.intellij.icons.AllIcons;
import com.intellij.lang.Language;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ignore.psi.IgnoreFile;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.FileViewProvider;
import com.intellij.util.ArrayUtilRt;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

public class IgnoreLanguage extends Language {
  public static final IgnoreLanguage INSTANCE = new IgnoreLanguage();

  @NonNls
  private static final String DOT = ".";

  @NotNull
  private final String myExtension;

  protected IgnoreLanguage() {
    this("IgnoreLang", "ignore");
  }

  protected IgnoreLanguage(@NotNull @NonNls String name, @NotNull @NonNls String extension) {
    super(INSTANCE, name, ArrayUtilRt.EMPTY_STRING_ARRAY);
    myExtension = extension;
  }

  @NotNull
  public String getExtension() {
    return myExtension;
  }

  /**
   * The ignore file filename.
   *
   * @return filename.
   */
  @NotNull
  public String getFilename() {
    return DOT + getExtension();
  }

  @NotNull
  @Override
  public String getDisplayName() {
    return getFilename() + " (" + getID() + ")";
  }

  @Nullable
  public Icon getIcon() {
    return AllIcons.Vcs.Ignore_file;
  }

  @NotNull
  public IgnoreFileType getFileType() {
    return IgnoreFileType.INSTANCE;
  }

  @NotNull
  public final IgnoreFile createFile(@NotNull FileViewProvider viewProvider) {
    return new IgnoreFile(viewProvider, getFileType());
  }

  /**
   * Returns <code>true</code> if `syntax: value` entry is supported by the language (i.e. Mercurial).
   *
   * @return <code>true</code> if `syntax: value` entry is supported
   */
  public boolean isSyntaxSupported() {
    return false;
  }

  /**
   * Returns default language syntax.
   *
   * @return default syntax
   */
  @NotNull
  public Syntax getDefaultSyntax() {
    return Syntax.GLOB;
  }

  /**
   * Returns affected root for the given ignore file.
   * For some ignore files the affected root is the same as the contained directory in which ignore file exist (e.g. .gitignore).
   * For some ignore files the affected root match to the whole repository root (e.g. .git/info/exclude).
   *
   * @param project
   * @param ignoreFile
   */
  @Nullable
  public VirtualFile getAffectedRoot(@NotNull Project project, @NotNull VirtualFile ignoreFile){
    return ignoreFile.getParent();
  }
}
