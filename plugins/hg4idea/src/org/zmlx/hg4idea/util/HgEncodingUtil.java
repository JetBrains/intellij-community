package org.zmlx.hg4idea.util;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

import static org.zmlx.hg4idea.HgVcs.HGENCODING;

/**
 * @author Kirill Likhodedov
 */
public class HgEncodingUtil {

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
    if (name.startsWith("x-M")) {
      return name.substring(2); // without "x-" prefix;
    }
    return name;
  }
}
