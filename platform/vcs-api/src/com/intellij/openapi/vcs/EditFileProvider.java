// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * VCS interface for invoking "check out"/"edit file" operations.
 *
 * @author lesya
 * @see AbstractVcs#getEditFileProvider()
 */
public interface EditFileProvider {
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
