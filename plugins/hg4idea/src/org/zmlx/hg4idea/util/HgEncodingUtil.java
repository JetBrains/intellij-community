// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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

  @NotNull
  public static Charset getDefaultCharset(@NotNull Project project) {
    if (HGENCODING != null && HGENCODING.length() > 0 && Charset.isSupported(HGENCODING)) {
      return Charset.forName(HGENCODING);
    }
    Charset defaultCharset = null;
    if (!project.isDisposed()) {
      defaultCharset = EncodingProjectManager.getInstance(project).getDefaultCharset();
    }
    return defaultCharset != null ? defaultCharset : Charset.defaultCharset();
  }

  @NotNull
  public static String getNameFor(@NotNull Charset charset) {
    //workaround for x_MacRoman encoding etc; todo: create map with encoding aliases because some encodings name are not supported by hg
    String name = charset.name();
    if (name.startsWith("x-M")) { //NON-NLS
      return name.substring(2); // without "x-" prefix;
    }
    return name;
  }
}
