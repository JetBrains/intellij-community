// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.xml.Attribute;
import com.intellij.util.xml.Convert;
import com.intellij.util.xml.GenericAttributeValue;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomDirSet extends AntDomFilesProviderImpl{

  @Attribute("dir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getDir();

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @Override
  protected @NotNull List<File> getFiles(@Nullable AntDomPattern pattern, Set<AntFilesProvider> processed) {
    assert pattern != null;
    final File singleFile = getCanonicalFile(getFile().getStringValue());
    if (singleFile == null || pattern.hasIncludePatterns()) {
      // if singleFile is specified, there are no implicit includes
      final File root = getCanonicalFile(getDir().getStringValue());
      if (root != null) {
        final ArrayList<File> files = new ArrayList<>();
        if (singleFile != null && singleFile.isDirectory()) {
          files.add(singleFile);
        }
        new FilesCollector().collectFiles(files, root, "", pattern);
        return files;
      }
    }

    if (singleFile != null && singleFile.isDirectory()) {
      return Collections.singletonList(singleFile);
    }

    return Collections.emptyList();
  }


  private static class FilesCollector {
    private static final int MAX_DIRS_TO_PROCESS = 100;
    private int myDirsProcessed = 0;

    public void collectFiles(List<? super File> container, File from, String relativePath, final AntDomPattern pattern) {
      if (myDirsProcessed > MAX_DIRS_TO_PROCESS) {
        return;
      }
      myDirsProcessed++;
      final File[] children = from.listFiles();
      if (children != null) {
        for (File child : children) {
          if (child.isDirectory()) {
            final String childPath = makePath(relativePath, child.getName());
            if (pattern.acceptPath(childPath)) {
              container.add(child);
            }
            if (pattern.couldBeIncluded(childPath)) {
              collectFiles(container, child, childPath, pattern);
            }
          }
        }
      }
    }

    private static String makePath(final String parentPath, final String name) {
      if (parentPath.isEmpty()) {
        return name;
      }
      return parentPath + "/" + name;
    }

  }

}
