// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentBinaryContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.ObjectUtils;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

import static com.intellij.util.ArrayUtil.EMPTY_BYTE_ARRAY;

/**
 * @author yole
 */
public class ShelvedBinaryFile implements JDOMExternalizable {
  public String BEFORE_PATH;
  public String AFTER_PATH;
  @Nullable public String SHELVED_PATH;         // null if binary file was deleted
  private Change myChange;

  ShelvedBinaryFile() {
  }

  public ShelvedBinaryFile(String beforePath, String afterPath, @Nullable String shelvedPath) {
    assert beforePath != null || afterPath != null;
    BEFORE_PATH = beforePath;
    AFTER_PATH = afterPath;
    SHELVED_PATH = shelvedPath;
    convertPathsToSystemIndependent();
  }

  @Nullable
  private static String convertToSystemIndependent(@Nullable String beforePath) {
    return beforePath != null ? FileUtil.toSystemIndependentName(beforePath) : null;
  }

  private void convertPathsToSystemIndependent() {
    BEFORE_PATH = convertToSystemIndependent(BEFORE_PATH);
    AFTER_PATH = convertToSystemIndependent(AFTER_PATH);
    SHELVED_PATH = convertToSystemIndependent(SHELVED_PATH);
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    convertPathsToSystemIndependent();
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
  }

  public FileStatus getFileStatus() {
    if (BEFORE_PATH == null) {
      return FileStatus.ADDED;
    }
    if (SHELVED_PATH == null) {
      return FileStatus.DELETED;
    }
    return FileStatus.MODIFIED;
  }

  @NotNull
  public Change createChange(@NotNull final Project project) {
    if (myChange == null) {
      ContentRevision before = null;
      ContentRevision after = null;
      final File baseDir = new File(project.getBaseDir().getPath());
      if (BEFORE_PATH != null) {
        final FilePath file = VcsUtil.getFilePath(new File(baseDir, BEFORE_PATH), false);
        before = new CurrentBinaryContentRevision(file) {
          @Override
          public byte @Nullable [] getBinaryContent() throws VcsException {
            return ObjectUtils.chooseNotNull(super.getBinaryContent(), EMPTY_BYTE_ARRAY);
          }

          @NotNull
          @Override
          public VcsRevisionNumber getRevisionNumber() {
            return new TextRevisionNumber(VcsBundle.message("local.version.title"));
          }
        };
      }
      if (AFTER_PATH != null) {
        after = createBinaryContentRevision(project);
      }
      myChange = new Change(before, after);
    }
    return myChange;
  }

  @NotNull
  ShelvedBinaryContentRevision createBinaryContentRevision(@NotNull Project project) {
    final FilePath file = VcsUtil.getFilePath(new File(project.getBasePath(), AFTER_PATH), false);
   return new ShelvedBinaryContentRevision(file, SHELVED_PATH);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    ShelvedBinaryFile that = (ShelvedBinaryFile)o;

    if (AFTER_PATH != null ? !AFTER_PATH.equals(that.AFTER_PATH) : that.AFTER_PATH != null) return false;
    if (BEFORE_PATH != null ? !BEFORE_PATH.equals(that.BEFORE_PATH) : that.BEFORE_PATH != null) return false;
    if (SHELVED_PATH != null ? !SHELVED_PATH.equals(that.SHELVED_PATH) : that.SHELVED_PATH != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = BEFORE_PATH != null ? BEFORE_PATH.hashCode() : 0;
    result = 31 * result + (AFTER_PATH != null ? AFTER_PATH.hashCode() : 0);
    result = 31 * result + (SHELVED_PATH != null ? SHELVED_PATH.hashCode() : 0);
    return result;
  }

  public String toString() {
    return FileUtil.toSystemDependentName(BEFORE_PATH == null ? AFTER_PATH : BEFORE_PATH);
  }
}