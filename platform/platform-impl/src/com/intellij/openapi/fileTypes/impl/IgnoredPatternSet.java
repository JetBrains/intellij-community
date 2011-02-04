/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * @author peter
 */
public class IgnoredPatternSet {
  private final Set<String> myMasks = new LinkedHashSet<String>();
  private final FileTypeAssocTable<Boolean> myIgnorePatterns = new FileTypeAssocTable<Boolean>().copy();

  Set<String> getIgnoreMasks() {
    return Collections.unmodifiableSet(myMasks);
  }

  void setIgnoreMasks(@NotNull String list) {
    clearPatterns();

    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      String ignoredFile = tokenizer.nextToken();
      if (ignoredFile != null) {
        addIgnoreMask(ignoredFile);
      }
    }

  }

  void addIgnoreMask(@NotNull String ignoredFile) {
    if (myIgnorePatterns.findAssociatedFileType(ignoredFile) == null) {
      myMasks.add(ignoredFile);
      myIgnorePatterns.addAssociation(FileTypeManager.parseFromString(ignoredFile), Boolean.TRUE);
    }
  }

  boolean isIgnored(@NotNull String fileName) {
    if (myIgnorePatterns.findAssociatedFileType(fileName) == Boolean.TRUE) {
      return true;
    }

    //Quite a hack, but still we need to have some name, which
    //won't be catched by VFS for sure.
    return fileName.endsWith(FileUtil.ASYNC_DELETE_EXTENSION);
  }

  void clearPatterns() {
    myMasks.clear();
    myIgnorePatterns.removeAllAssociations(Boolean.TRUE);
  }
}
