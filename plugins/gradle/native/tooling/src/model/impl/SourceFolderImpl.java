// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl;

import org.jetbrains.plugins.gradle.model.FilePatternSet;
import org.jetbrains.plugins.gradle.model.FilePatternSetImpl;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.SourceFolder;

import java.io.File;

/**
 * @author Vladislav.Soroka
 */
public class SourceFolderImpl implements SourceFolder {
  private final File myBaseDir;
  private final FilePatternSet myPatterns;

  public SourceFolderImpl(File dir, FilePatternSet patterns) {
    myBaseDir = dir;
    myPatterns = patterns;
  }

  public SourceFolderImpl(SourceFolder folder) {
    this(folder.getBaseDir(), folder.getPatterns() != null
                              ? new FilePatternSetImpl(folder.getPatterns().getIncludes(), folder.getPatterns().getExcludes()) : null);
  }

  @Override
  public File getBaseDir() {
    return myBaseDir;
  }

  @Override
  public FilePatternSet getPatterns() {
    return myPatterns;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    SourceFolderImpl folder = (SourceFolderImpl)o;

    if (myBaseDir != null ? !myBaseDir.equals(folder.myBaseDir) : folder.myBaseDir != null) return false;
    if (myPatterns != null ? !myPatterns.equals(folder.myPatterns) : folder.myPatterns != null) return false;

    return true;
  }

  @Override
  public int hashCode() {
    int result = myBaseDir != null ? myBaseDir.hashCode() : 0;
    result = 31 * result + (myPatterns != null ? myPatterns.hashCode() : 0);
    return result;
  }
}
