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
import com.intellij.util.Function;
import com.intellij.util.containers.Convertor;
import com.intellij.vcsUtil.VcsUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static com.intellij.util.containers.ContainerUtil.map;

/**
 * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#map(Collection, Function)}
 */
@Deprecated
public class ObjectsConvertor {

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#map(Collection, Function)}
   */
  @Deprecated
  @NotNull
  public static List<VirtualFile> fp2vf(@NotNull Collection<? extends FilePath> in) {
    return map(in, FilePath::getVirtualFile);
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#map(Collection, Function)}
   */
  @Deprecated
  @NotNull
  public static List<FilePath> vf2fp(@NotNull List<? extends VirtualFile> in) {
    return map(in, VcsUtil::getFilePath);
  }

  /**
   * @deprecated Use {@link com.intellij.util.containers.ContainerUtil#map(Collection, Function)}
   */
  @Deprecated
  @NotNull
  public static <T, S> List<S> convert(@NotNull Collection<? extends T> in, @NotNull Convertor<? super T, ? extends S> convertor) {
    return map(in, convertor::convert);
  }
}
