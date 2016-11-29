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
import com.intellij.util.NotNullFunction;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ObjectsConvertor {

  public static final Convertor<FilePath, VirtualFile> FILEPATH_TO_VIRTUAL = new Convertor<FilePath, VirtualFile>() {
    public VirtualFile convert(FilePath fp) {
      return fp.getVirtualFile();
    }
  };

  public static final Convertor<VirtualFile, FilePath> VIRTUAL_FILEPATH = new Convertor<VirtualFile, FilePath>() {
    public FilePath convert(VirtualFile vf) {
      return VcsUtil.getFilePath(vf);
    }
  };

  public static final NotNullFunction<Object, Boolean> NOT_NULL = new NotNullFunction<Object, Boolean>() {
    @NotNull
    public Boolean fun(final Object o) {
      return o != null;
    }
  };

  public static List<VirtualFile> fp2vf(@NotNull final Collection<FilePath> in) {
    return convert(in, FILEPATH_TO_VIRTUAL);
  }

  public static List<FilePath> vf2fp(@NotNull final List<VirtualFile> in) {
    return convert(in, VIRTUAL_FILEPATH);
  }

  public static <T,S> List<S> convert(@NotNull final Collection<T> in, final Convertor<T,S> convertor) {
    return convert(in, convertor, null);
  }

  public static <T,U, S extends U> List<S> convert(@NotNull final Collection<T> in, final Convertor<T,S> convertor,
                                                   @Nullable final NotNullFunction<U, Boolean> outFilter) {
    final List<S> out = new ArrayList<>();
    for (T t : in) {
      final S converted = convertor.convert(t);
      if ((outFilter != null) && (! Boolean.TRUE.equals(outFilter.fun(converted)))) continue;
      out.add(converted);
    }
    return out;
  }
}
