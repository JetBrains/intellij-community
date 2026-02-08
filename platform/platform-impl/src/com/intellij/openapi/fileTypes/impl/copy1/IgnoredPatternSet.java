// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl.copy1;

import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTableUtil;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtilRt;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

/**
 * DO NOT USE
 * TODO remove in favor of a standard {@link com.intellij.openapi.fileTypes.impl.IgnoredPatternSet}
 * as soon as it's ported to {@link com.intellij.concurrency.ConcurrentCollectionFactory#createConcurrentMap}
 * Until then, this copy should stay because it's more scalable
 */
@ApiStatus.Internal
public final class IgnoredPatternSet {
  private final Set<String> masks;
  private final FileTypeAssocTable<Boolean> ignorePatterns = FileTypeAssocTableUtil.newScalableFileTypeAssocTable();

  public IgnoredPatternSet(@NotNull List<String> initialMasks) {
    FileNameMatcherFactory fileNameMatcherFactory = null;
    this.masks = new LinkedHashSet<>(initialMasks.size());
    for (String ignoredFile : initialMasks) {
      if (ignorePatterns.findAssociatedFileType(ignoredFile) == null) {
        this.masks.add(ignoredFile);
        if (fileNameMatcherFactory == null) {
          fileNameMatcherFactory = FileNameMatcherFactory.getInstance();
        }
        ignorePatterns.addAssociation(fileNameMatcherFactory.createMatcher(ignoredFile), Boolean.TRUE);
      }
    }
  }

  @ApiStatus.Internal
  public @NotNull Set<String> getIgnoreMasks() {
    return Collections.unmodifiableSet(masks);
  }

  public void setIgnoreMasks(@NotNull String list) {
    clearPatterns();

    StringTokenizer tokenizer = new StringTokenizer(list, ";");
    while (tokenizer.hasMoreTokens()) {
      addIgnoreMask(tokenizer.nextToken());
    }
  }

  @ApiStatus.Internal
  public void addIgnoreMask(@NotNull String ignoredFile) {
    if (ignorePatterns.findAssociatedFileType(ignoredFile) == null) {
      masks.add(ignoredFile);
      ignorePatterns.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(ignoredFile), Boolean.TRUE);
    }
  }

  public boolean isIgnored(@NotNull CharSequence fileName) {
    if (ignorePatterns.findAssociatedFileType(fileName) == Boolean.TRUE) {
      return true;
    }

    //Quite a hack, but still we need to have some name, which
    //won't be caught by VFS for sure.
    return StringUtilRt.endsWith(fileName, FileUtil.ASYNC_DELETE_EXTENSION);
  }

  @ApiStatus.Internal
  public void clearPatterns() {
    masks.clear();
    ignorePatterns.removeAllAssociations(Boolean.TRUE);
  }
}
