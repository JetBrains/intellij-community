package org.jetbrains.plugins.groovy.gpp;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.impl.light.LightClass;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitution;
import org.jetbrains.plugins.groovy.lang.psi.GrClassSubstitutor;

/**
 * @author peter
 */
public class GppClassSubstitutor extends GrClassSubstitutor {

  @Override
  public GrClassSubstitution substituteClass(@NotNull PsiClass base) {
    final PsiModifierList modifierList = base.getModifierList();
    if (modifierList != null && modifierList.findAnnotation("groovy.lang.Trait") != null) {
      return new TraitClass(base);
    }
    return null;
  }

  public static class TraitClass extends LightClass implements GrClassSubstitution {
    public TraitClass(PsiClass base) {
      super(base, GroovyFileType.GROOVY_LANGUAGE);
    }

    @Override
    public boolean isInterface() {
      return true;
    }

  }
}
