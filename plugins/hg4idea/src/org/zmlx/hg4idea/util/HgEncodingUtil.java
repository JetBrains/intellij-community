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

  public static Charset getDefaultCharset() {
    return SystemInfo.isWindows ? getCharsetForNameOrDefault(WINDOWS_DEFAULT_CHARSET) : getCharsetForNameOrDefault(UNIX_DEFAULT_CHARSET);
  }
  
  public static String getDefaultCharsetName() {
    return SystemInfo.isWindows ? WINDOWS_DEFAULT_CHARSET : UNIX_DEFAULT_CHARSET;
  }

  private static Charset getCharsetForNameOrDefault(@NotNull String name) {
    try {
      return Charset.forName(name);
    }
    catch (Exception e) {
      return Charset.defaultCharset();
    }
  }
  
}
