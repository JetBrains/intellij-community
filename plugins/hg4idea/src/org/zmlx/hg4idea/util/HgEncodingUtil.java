package org.zmlx.hg4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author Kirill Likhodedov
 */
public class HgEncodingUtil {

  private static final Logger LOG = Logger.getInstance(HgEncodingUtil.class);

  private static final String WINDOWS_DEFAULT_CHARSET = "cp1251";
  private static final String UNIX_DEFAULT_CHARSET = "UTF-8";

  private static final Charset DEFAULT_CHARSET = updateDefaultEncoding();

  public static Charset updateDefaultEncoding() {
    String name = SystemInfo.isWindows ? WINDOWS_DEFAULT_CHARSET : UNIX_DEFAULT_CHARSET;
    try {
      String enc = System.getenv("HGENCODING");
      if (enc != null && enc.length() > 0 && Charset.isSupported(enc)) {
        name = enc;
      }
      return Charset.forName(name);
    }
    catch (Exception e) {
      LOG.info("Couldn't find encoding " + name, e);
    }
    return Charset.defaultCharset();
  }

  private HgEncodingUtil() {
  }

  /**
   * Returns the default charset for Mercurial.
   * It is value of{@code HGENCODING} or  cp1251 for Windows, and UTF-8 for Unix-like systems.
   *
   * @return user encoding/cp1251 for windows/UTF-8 for Unix-like systems.
   */
  @NotNull
  public static Charset getDefaultCharset() {
    return DEFAULT_CHARSET;
  }
}
