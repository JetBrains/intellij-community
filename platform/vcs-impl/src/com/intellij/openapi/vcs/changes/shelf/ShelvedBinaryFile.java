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
import com.intellij.openapi.vcs.changes.Change;
import com.intellij.openapi.vcs.changes.ContentRevision;
import com.intellij.openapi.vcs.changes.CurrentBinaryContentRevision;
import com.intellij.openapi.vcs.changes.TextRevisionNumber;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;

/**
 * @author yole
 */
public class ShelvedBinaryFile implements JDOMExternalizable {
  public String BEFORE_PATH;
  public String AFTER_PATH;
  @Nullable public String SHELVED_PATH;         // null if binary file was deleted

  public ShelvedBinaryFile() {
  }

  public ShelvedBinaryFile(final String beforePath, final String afterPath, @Nullable final String shelvedPath) {
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

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    convertPathsToSystemIndependent();
  }

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

  public Change createChange(final Project project) {
    ContentRevision before = null;
    ContentRevision after = null;
    final File baseDir = new File(project.getBaseDir().getPath());
    if (BEFORE_PATH != null) {
      final FilePath file = VcsUtil.getFilePath(new File(baseDir, BEFORE_PATH), false);
      before = new CurrentBinaryContentRevision(file) {
        @NotNull
        @Override
        public VcsRevisionNumber getRevisionNumber() {
          return new TextRevisionNumber(VcsBundle.message("local.version.title"));
        }
      };
    }
    if (AFTER_PATH != null) {
      final FilePath file = VcsUtil.getFilePath(new File(baseDir, AFTER_PATH), false);
      after = new ShelvedBinaryContentRevision(file, SHELVED_PATH);
    }
    return new Change(before, after);
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
}