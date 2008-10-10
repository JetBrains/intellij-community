package org.jetbrains.plugins.gant.util;

import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.gant.GantFileType;

/**
 * @author ilyas
 */
public class GantUtils {
  private GantUtils() {
  }

  public static boolean isGantScriptFile(PsiFile file) {
    if (file instanceof GroovyFile) {
      GroovyFile groovyFile = (GroovyFile)file;
      if (!groovyFile.isScript()) return false;
      String name = file.getName();
      return  name.endsWith(GantFileType.DEFAULT_EXTENSION);
    }
    return false;
  }

  public static boolean isGantScriptCompiledName(String s) {
    return s != null && s.endsWith("_gant");
  }
}
