// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl;

import com.intellij.concurrency.ConcurrentCollectionFactory;
import com.intellij.concurrency.JobSchedulerImpl;
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable;
import com.intellij.util.containers.HashingStrategy;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * DO NOT USE
 * TODO remove in favor of a standard {@link com.intellij.openapi.fileTypes.impl.FileTypeAssocTableUtil}
 * as soon as it's ported to {@link com.intellij.concurrency.ConcurrentCollectionFactory#createConcurrentMap}
 * Until then, this copy should stay because it's more scalable
 */
@ApiStatus.Internal
public final class FileTypeAssocTableUtil {
  public static @NotNull <T> FileTypeAssocTable<T> newScalableFileTypeAssocTable() {
    return new FileTypeAssocTable<>((source, caseSensitive) -> createScalableCharSequenceConcurrentMap(source, caseSensitive));
  }

  private static @NotNull <T> Map<CharSequence, T> createScalableCharSequenceConcurrentMap(@NotNull Map<? extends CharSequence, ? extends T> source, boolean caseSensitive) {
    HashingStrategy<CharSequence> hashingStrategy = caseSensitive ? HashingStrategy.caseSensitiveCharSequence() : HashingStrategy.caseInsensitiveCharSequence();
    Map<CharSequence, T> map = ConcurrentCollectionFactory.createConcurrentMap(source.size(),
                                                                               0.5f,
                                                                               JobSchedulerImpl.getCPUCoresCount(),
                                                                               hashingStrategy);
    map.putAll(source);
    return map;
  }
}