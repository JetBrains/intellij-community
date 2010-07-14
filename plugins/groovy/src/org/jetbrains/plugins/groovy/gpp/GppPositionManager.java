package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.sun.jdi.ReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.debugger.ScriptPositionManagerHelper;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class GppPositionManager extends ScriptPositionManagerHelper {
  private static final String TRAIT_IMPL = "$TraitImpl";

  @Override
  public boolean isAppropriateRuntimeName(@NotNull String runtimeName) {
    return runtimeName.endsWith(TRAIT_IMPL);
  }

  @Override
  public boolean isAppropriateScriptFile(@NotNull PsiFile scriptFile) {
    return false;
  }

  @NotNull
  @Override
  public String getRuntimeScriptName(@NotNull String originalName, GroovyFile groovyFile) {
    return originalName;
  }

  @Override
  public PsiFile getExtraScriptIfNotFound(ReferenceType refType, @NotNull String runtimeName, Project project) {
    final PsiClass trait =
      JavaPsiFacade.getInstance(project).findClass(StringUtil.trimEnd(runtimeName, TRAIT_IMPL), GlobalSearchScope.allScope(project));
    if (trait != null) {
      return trait.getContainingFile();
    }
    return null;
  }

  public String customizeClassName(PsiClass psiClass) {
    if (GrClassSubstitutor.getSubstitutedClass(psiClass) instanceof GppClassSubstitutor.TraitClass) {
      final String qname = psiClass.getQualifiedName();
      if (qname != null) {
        return qname + TRAIT_IMPL;
      }
    }
    return null;
  }

}
