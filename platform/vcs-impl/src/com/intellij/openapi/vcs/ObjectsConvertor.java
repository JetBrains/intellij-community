// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
public final class ObjectsConvertor {

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
