package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.NameHint;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author peter
 */
public class NonCodeMembersHolder {
  private final GrTypeDefinition myPsiClass;

  public NonCodeMembersHolder(@NotNull String classText, Project project) {
    final GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(project);
    myPsiClass = factory.createGroovyFile("class GroovyEnhanced {\n" + classText + "}", false, null).getTypeDefinitions()[0];
  }

  public boolean processMembers(PsiScopeProcessor processor) {
    final NameHint nameHint = processor.getHint(NameHint.KEY);
    final String expectedName = nameHint == null ? null : nameHint.getName(ResolveState.initial());

    for (PsiMethod method : myPsiClass.getMethods()) {
      if ((expectedName == null || expectedName.equals(method.getName())) && !processor.execute(method, ResolveState.initial())) {
        return false;
      }
    }
    for (final PsiField field : myPsiClass.getFields()) {
      if ((expectedName == null || expectedName.equals(field.getName())) && !processor.execute(field, ResolveState.initial())) return false;
    }
    return true;
  }
}
