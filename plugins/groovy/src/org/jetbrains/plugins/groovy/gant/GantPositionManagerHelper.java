package org.jetbrains.plugins.groovy.gant;

import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author ilyas
 */
public class GantPositionManagerHelper implements ScriptPositionManagerHelper {

  @NonNls private static final String GANT_SUFFIX = "_gant";

  public boolean isAppropriateRuntimeName(@NotNull final String runtimeName) {
    return runtimeName.endsWith(GANT_SUFFIX);
  }

  @NotNull
  public String getOriginalScriptName(ReferenceType refType, @NotNull final String runtimeName) {
    return StringUtil.trimEnd(runtimeName, GANT_SUFFIX);
  }

  public boolean isAppropriateScriptFile(@NotNull final PsiFile scriptFile) {
    return GantUtils.isGantScriptFile(scriptFile);
  }

  @NotNull
  public String getRuntimeScriptName(@NotNull final String originalName, GroovyFile groovyFile) {
    final String version =
      GantUtils.getGantVersion(GantUtils.getSDKInstallPath(ModuleUtil.findModuleForPsiElement(groovyFile), groovyFile.getProject()));
    if (version.compareToIgnoreCase("1.5") >= 0) {
      return originalName;
    }

    return originalName + GANT_SUFFIX;
  }

  public PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull final String runtimeName, final Project project) {
    PsiFile[] files = FilenameIndex.getFilesByName(project, StringUtil.trimEnd(runtimeName, GANT_SUFFIX) + "." + GantScriptType.DEFAULT_EXTENSION,
                                                   GlobalSearchScope.allScope(project));
    if (files.length == 1) return files[0];
    return null;
  }
}
