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
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * VCS interface for invoking "check out"/"edit file" operations.
 *
 * @author lesya
 * @see com.intellij.openapi.vcs.AbstractVcs#getEditFileProvider()
 */
public interface EditFileProvider extends VcsProviderMarker {
  /**
   * Initiates the edit / checkout operation for the specified files.
   *
   * @param files the list of files to edit or check out.
   * @throws VcsException if the operation fails for some reason.
   */
  void editFiles(VirtualFile[] files) throws VcsException;

  /**
   * Returns the text shown to the user to confirm the check out / edit file
   * operation.
   *
   * @return the operation confirmation text.
   */
  String getRequestText();
}
