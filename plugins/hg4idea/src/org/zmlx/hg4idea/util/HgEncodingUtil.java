package org.zmlx.hg4idea.util;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author Kirill Likhodedov
 */
public class HgEncodingUtil {
  
  private static final String WINDOWS_DEFAULT_CHARSET = "cp1251";
  private static final String UNIX_DEFAULT_CHARSET = "UTF-8";

  private HgEncodingUtil() {
  }

  /**
   * Returns the default charset for Mercurial.
   * It is cp1251 for Windows, and UTF-8 for Unix-like systems.
   * The {@code HGENCODING} environment variable is not considered, because being set for IDEA it is inherited by the hg process
   * spawned by IDEA.
   * @return cp1251 for windows / UTF-8 for Unix-like systems.
   */
  @NotNull
  public static Charset getDefaultCharset() {
    return SystemInfo.isWindows ? getCharsetForNameOrDefault(WINDOWS_DEFAULT_CHARSET) : getCharsetForNameOrDefault(UNIX_DEFAULT_CHARSET);
  }

  @NotNull
  private static Charset getCharsetForNameOrDefault(@NotNull String name) {
    try {
      return Charset.forName(name);
    }
    catch (Exception e) {
      return Charset.defaultCharset();
    }
  }
}
