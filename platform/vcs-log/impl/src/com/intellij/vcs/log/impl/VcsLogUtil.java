/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.vcs.log.impl;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.containers.MultiMap;
import com.intellij.vcs.log.VcsRef;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.Comparator;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author Kirill Likhodedov
 */
public class VcsLogUtil {
  @NotNull
  public static MultiMap<VirtualFile, VcsRef> groupRefsByRoot(@NotNull Collection<VcsRef> refs) {
    MultiMap<VirtualFile, VcsRef> map = new MultiMap<VirtualFile, VcsRef>() {
      @Override
      protected Map<VirtualFile, Collection<VcsRef>> createMap() {
        return new TreeMap<VirtualFile, Collection<VcsRef>>(new Comparator<VirtualFile>() { // TODO common to VCS root sorting method
          @Override
          public int compare(VirtualFile o1, VirtualFile o2) {
            return o1.getPresentableUrl().compareTo(o2.getPresentableUrl());
          }
        });
      }
    };
    for (VcsRef ref : refs) {
      map.putValue(ref.getRoot(), ref);
    }
    return map;
  }
}
