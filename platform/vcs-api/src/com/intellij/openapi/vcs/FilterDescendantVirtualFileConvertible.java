/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.util.containers.ComparatorDelegate;
import com.intellij.util.containers.Convertor;

import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class FilterDescendantVirtualFileConvertible<T> extends AbstractFilterChildren<T> {
  private final ComparatorDelegate<T, VirtualFile> myComparator;
  private final Convertor<T, VirtualFile> myConvertor;

  public FilterDescendantVirtualFileConvertible(final Convertor<T, VirtualFile> convertor, final Comparator<VirtualFile> comparator) {
    myConvertor = convertor;
    myComparator = new ComparatorDelegate<>(myConvertor, comparator);
  }

  @Override
  protected void sortAscending(final List<T> ts) {
    Collections.sort(ts, myComparator);
  }

  @Override
  protected boolean isAncestor(final T parent, final T child) {
    return VfsUtil.isAncestor(myConvertor.convert(parent), myConvertor.convert(child), false);
  }
}
