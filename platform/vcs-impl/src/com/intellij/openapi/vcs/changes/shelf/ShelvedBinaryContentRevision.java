/*
 * Copyright 2000-2012 JetBrains s.r.o.
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

import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;

@ApiStatus.Internal
public class ShelvedBinaryContentRevision extends SimpleBinaryContentRevision {
  private final String myShelvedContentPath;

  public ShelvedBinaryContentRevision(FilePath path, final String shelvedContentPath) {
    super(path, VcsBundle.message("shelved.version.name"));
    myShelvedContentPath = shelvedContentPath;
  }

  @Override
  public byte @NotNull [] getBinaryContent() throws VcsException {
    try {
      return FileUtil.loadFileBytes(new File(myShelvedContentPath));
    }
    catch (IOException e) {
      throw new VcsException(e);
    }
  }
}
