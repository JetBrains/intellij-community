// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.project.Project;
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
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.Map;
import java.util.Objects;

import static com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList.*;
import static com.intellij.util.ArrayUtil.EMPTY_BYTE_ARRAY;

@ApiStatus.Internal
public final class ShelvedBinaryFile {
  private static final String BEFORE_PATH_FIELD_NAME = "BEFORE_PATH";
  private static final String AFTER_PATH_FIELD_NAME = "AFTER_PATH";
  private static final String SHELVED_PATH_FIELD_NAME = "SHELVED_PATH";

  public final String BEFORE_PATH;
  public final String AFTER_PATH;
  @Nullable public final String SHELVED_PATH;         // null if binary file was deleted

  private Change myChange;

  public ShelvedBinaryFile(String beforePath, String afterPath, @Nullable String shelvedPath) {
    assert beforePath != null || afterPath != null;
    BEFORE_PATH = convertToSystemIndependent(beforePath);
    AFTER_PATH = convertToSystemIndependent(afterPath);
    SHELVED_PATH = convertToSystemIndependent(shelvedPath);
  }

  @Nullable
  private static String convertToSystemIndependent(@Nullable String beforePath) {
    return beforePath != null ? FileUtil.toSystemIndependentName(beforePath) : null;
  }

  public static @NotNull ShelvedBinaryFile readExternal(@NotNull Element element, @NotNull PathMacroSubstitutor pathMacroSubstitutor) {
    String beforePath = null;
    String afterPath = null;
    String shelvedPath = null;
    for (Map.Entry<String, String> field : readFields(element).entrySet()) {
      String value = pathMacroSubstitutor.expandPath(Objects.requireNonNull(field.getValue()));
      if (field.getKey().equals(BEFORE_PATH_FIELD_NAME)) {
        beforePath = value;
      }
      else if (field.getKey().equals(AFTER_PATH_FIELD_NAME)) {
        afterPath = value;
      }
      else if (field.getKey().equals(SHELVED_PATH_FIELD_NAME)) {
        shelvedPath = value;
      }
    }
    return new ShelvedBinaryFile(beforePath, afterPath, shelvedPath);
  }

  public void writeExternal(@NotNull Element element, @Nullable PathMacroSubstitutor pathMacroSubstitutor) {
    writeField(element, BEFORE_PATH_FIELD_NAME, collapsePath(BEFORE_PATH, pathMacroSubstitutor));
    writeField(element, AFTER_PATH_FIELD_NAME, collapsePath(AFTER_PATH, pathMacroSubstitutor));
    writeField(element, SHELVED_PATH_FIELD_NAME, collapsePath(SHELVED_PATH, pathMacroSubstitutor));
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

    if (!Objects.equals(AFTER_PATH, that.AFTER_PATH)) return false;
    if (!Objects.equals(BEFORE_PATH, that.BEFORE_PATH)) return false;
    if (!Objects.equals(SHELVED_PATH, that.SHELVED_PATH)) return false;

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