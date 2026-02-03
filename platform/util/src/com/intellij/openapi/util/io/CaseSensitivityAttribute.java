// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.util.io;

import java.nio.file.attribute.BasicFileAttributes;

/**
 * Sometimes a physical file system may know about the case sensitivity of children for a directory
 * as one of its attributes. In this case, the returned attributes must implement this interface
 * in order for VFS to recognize it and work more efficiently.
 */
public interface CaseSensitivityAttribute extends BasicFileAttributes {
  /**
   * Relevant for a directory.
   * @return case sensitivity for children of the requested directory.
   * @throws IllegalStateException if the attributes are requested for something other than a directory
   */
  FileAttributes.CaseSensitivity getCaseSensitivity() throws IllegalStateException;
}
