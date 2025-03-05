// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs;

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static java.util.Comparator.comparing;

public class FilterDescendantVirtualFileConvertible<T> extends AbstractFilterChildren<T> {
  private final @NotNull Comparator<? super T> myComparator;
  private final @NotNull Function<? super T, ? extends VirtualFile> myConvertor;

  public FilterDescendantVirtualFileConvertible(@NotNull Function<? super T, ? extends VirtualFile> convertor, @NotNull Comparator<? super VirtualFile> comparator) {
    myConvertor = convertor;
    myComparator = comparing(myConvertor, comparator);
  }

  @Override
  @Contract(mutates = "param1")
  protected void sortAscending(@NotNull List<? extends T> ts) {
    ts.sort(myComparator);
  }

  @Override
  protected boolean isAncestor(final T parent, final T child) {
    return VfsUtil.isAncestor(myConvertor.apply(parent), myConvertor.apply(child), false);
  }
}
