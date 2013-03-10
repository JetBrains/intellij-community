package org.zmlx.hg4idea.util;

import com.intellij.openapi.util.SystemInfo;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author Kirill Likhodedov
 */
public class HgEncodingUtil {

  private static final Charset DEFAULT_CHARSET;

  static {
    // next in line is HGENCODING in environment
    String enc = System.getenv("HGENCODING");

    // next is platform encoding as available in JDK
    Charset cs = SystemInfo.isWindows ? Charset.defaultCharset() : Charset.forName("UTF-8");
    try {
      if (enc != null && enc.length() > 0 && Charset.isSupported(enc)) {
        cs = Charset.forName(enc);
      }
    } catch (Exception e) {
      cs = SystemInfo.isWindows ? Charset.defaultCharset() : Charset.forName("UTF-8");
    } finally {
      DEFAULT_CHARSET = cs;
    }
  }

  private HgEncodingUtil() {
  }

  /**
   * The default encoding which is used by the current environment.
   * <b>Note</b>: Python's encoding isn't 1-1 with Charset.name() so do not store
   * {@link java.nio.charset.Charset}.
   *
   * @return a valid encoding name, never null.
   */
  @NotNull
  public static Charset getDefaultCharset() {
    return DEFAULT_CHARSET;
  }
}
