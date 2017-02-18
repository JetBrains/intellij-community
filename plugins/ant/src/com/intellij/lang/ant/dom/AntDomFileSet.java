/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.lang.ant.dom;

import com.intellij.lang.ant.AntFilesProvider;
import com.intellij.psi.PsiFileSystemItem;
import com.intellij.util.StringBuilderSpinAllocator;
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
 *         Date: Jun 22, 2010
 */
public abstract class AntDomFileSet extends AntDomFilesProviderImpl{
  @Attribute("dir")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getDir();

  @Attribute("file")
  @Convert(value = AntPathConverter.class)
  public abstract GenericAttributeValue<PsiFileSystemItem> getFile();

  @NotNull protected List<File> getFiles(@Nullable AntDomPattern pattern, Set<AntFilesProvider> processed) {
    assert pattern != null;
    final File singleFile = getCanonicalFile(getFile().getStringValue());
    if (singleFile == null || pattern.hasIncludePatterns()) {
      // if singleFile is specified, there are no implicit includes
      final File root = getCanonicalFile(getDir().getStringValue());
      if (root != null) {
        final ArrayList<File> files = new ArrayList<>();
        if (singleFile != null) {
          files.add(singleFile);
        }
        new FilesCollector().collectFiles(files, root, "", pattern);
        return files;
      }
    }
    if (singleFile != null) {
      return Collections.singletonList(singleFile);
    }
    return Collections.emptyList();
  }

  private static class FilesCollector {
    private static final int MAX_DIRS_TO_PROCESS = 100;
    private int myDirsProcessed = 0;
    private boolean myDirCheckEnabled = false;

    public void collectFiles(List<File> container, File from, String relativePath, final AntDomPattern pattern) {
      if (myDirsProcessed > MAX_DIRS_TO_PROCESS) {
        return;
      }
      final File[] children = from.listFiles();
      if (children != null && children.length > 0) {
        if (myDirCheckEnabled) {
          if (!pattern.couldBeIncluded(relativePath)) {
            return;
          }
        }
        else {
          myDirCheckEnabled = true;
        }
        myDirsProcessed++;
        for (File child : children) {
          final String childPath = makePath(relativePath, child.getName());
          if (pattern.acceptPath(childPath)) {
            container.add(child);
          }
          collectFiles(container, child, childPath, pattern);
        }
      }
    }

    private static String makePath(final String parentPath, final String name) {
      if (parentPath.length() == 0) {
        return name;
      }
      final StringBuilder builder = StringBuilderSpinAllocator.alloc();
      try {
        return builder.append(parentPath).append("/").append(name).toString();
      }
      finally {
        StringBuilderSpinAllocator.dispose(builder);
      }
    }

  }

}
