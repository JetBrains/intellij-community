package org.jetbrains.plugins.gant;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GantUtils {
  private GantUtils() {
  }

  public static boolean isGantScriptFile(PsiFile file) {
    if (file instanceof GroovyFile) {
      GroovyFile groovyFile = (GroovyFile)file;
      VirtualFile virtualFile = groovyFile.getVirtualFile();
      if (!groovyFile.isScript()) return false;
      return virtualFile != null && GantFileType.DEFAULT_EXTENSION.equals(virtualFile.getExtension());
    }
    return false;
  }
}
