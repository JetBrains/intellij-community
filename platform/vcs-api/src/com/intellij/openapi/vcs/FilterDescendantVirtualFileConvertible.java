/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.Comparator;
import java.util.List;
import java.util.function.Function;

import static java.util.Collections.sort;
import static java.util.Comparator.comparing;

public class FilterDescendantVirtualFileConvertible<T> extends AbstractFilterChildren<T> {
  @NotNull private final Comparator<T> myComparator;
  @NotNull private final Function<T, VirtualFile> myConvertor;

  public FilterDescendantVirtualFileConvertible(@NotNull Function<T, VirtualFile> convertor, @NotNull Comparator<VirtualFile> comparator) {
    myConvertor = convertor;
    myComparator = comparing(myConvertor, comparator);
  }

  @Override
  protected void sortAscending(@NotNull List<T> ts) {
    sort(ts, myComparator);
  }

  @Override
  protected boolean isAncestor(final T parent, final T child) {
    return VfsUtil.isAncestor(myConvertor.apply(parent), myConvertor.apply(child), false);
  }
}
