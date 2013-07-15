package org.zmlx.hg4idea.util;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.encoding.EncodingProjectManager;
import org.jetbrains.annotations.NotNull;

import java.nio.charset.Charset;

/**
 * @author Kirill Likhodedov
 */
public class HgEncodingUtil {

  private static final Logger LOG = Logger.getInstance(HgEncodingUtil.class);

  @NotNull
  public static Charset getDefaultCharset(@NotNull Project project) {
    String name = "";
    try {
      String enc = System.getenv("HGENCODING");
      if (enc != null && enc.length() > 0 && Charset.isSupported(enc)) {
        name = enc;
      }
      else {
        name = EncodingProjectManager.getInstance(project).getDefaultCharsetName();
      }
      if (!StringUtil.isEmptyOrSpaces(name)) {
        return Charset.forName(name);
      }
    }
    catch (Exception e) {
      LOG.info("Couldn't find encoding " + name, e);
    }
    return Charset.defaultCharset();
  }
}
