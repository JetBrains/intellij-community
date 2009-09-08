package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GantPositionManagerHelper extends ScriptPositionManagerHelper {

  public boolean isAppropriateRuntimeName(@NotNull final String runtimeName) {
    return true;
  }

  public boolean isAppropriateScriptFile(@NotNull final PsiFile scriptFile) {
    return GantUtils.isGantScriptFile(scriptFile);
  }

  @NotNull
  public String getRuntimeScriptName(@NotNull final String originalName, GroovyFile groovyFile) {
    return originalName;
  }

  public PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull final String runtimeName, final Project project) {
    PsiFile[] files = FilenameIndex.getFilesByName(project, runtimeName + "." + GantScriptType.DEFAULT_EXTENSION,
                                                   GlobalSearchScope.allScope(project));
    if (files.length == 1) return files[0];
    return null;
  }
}
