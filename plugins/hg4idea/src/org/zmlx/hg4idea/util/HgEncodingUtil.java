// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import static org.zmlx.hg4idea.HgVcs.HGENCODING;

/**
 * @author Kirill Likhodedov
 */
public final class HgEncodingUtil {

  public static @NotNull Charset getDefaultCharset(@NotNull Project project) {
    if (HGENCODING != null && !HGENCODING.isEmpty() && Charset.isSupported(HGENCODING)) {
      return Charset.forName(HGENCODING);
    }
    Charset defaultCharset = null;
    if (!project.isDisposed()) {
      defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    }
    return defaultCharset != null ? defaultCharset : Charset.defaultCharset();
  }

  public static @NotNull String getNameFor(@NotNull Charset charset) {
    //workaround for x_MacRoman encoding etc; todo: create map with encoding aliases because some encodings name are not supported by hg
    String name = charset.name();
    if (name.startsWith("x-M")) { //NON-NLS
      return name.substring(2); // without "x-" prefix;
    }
    return name;
  }
}
