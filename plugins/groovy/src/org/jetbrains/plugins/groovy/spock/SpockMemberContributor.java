package org.jetbrains.plugins.groovy.spock;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveState;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SpockMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) return;

    GrMethod method = PsiTreeUtil.getParentOfType(place, GrMethod.class);
    if (method == null) return;

    if (aClass != method.getContainingClass()) return;

    Map<String, SpockVariableDescriptor> cachedValue = SpockUtils.getVariableMap(method);

    String nameHint = ResolveUtil.getNameHint(processor);
    if (nameHint == null) {
      for (SpockVariableDescriptor spockVar : cachedValue.values()) {
        if (!processor.execute(spockVar.getVariable(), state)) return;
      }
    }
    else {
      SpockVariableDescriptor spockVar = cachedValue.get(nameHint);
      if (spockVar != null && spockVar.getNavigationElement() != place) {
        if (!processor.execute(spockVar.getVariable(), state)) return;
      }
    }
  }

  @Override
  public String getParentClassName() {
    return SpockUtils.SPEC_CLASS_NAME;
  }
}
