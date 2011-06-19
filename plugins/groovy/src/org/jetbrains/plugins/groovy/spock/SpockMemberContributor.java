package org.jetbrains.plugins.groovy.spock;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrClassDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.util.*;

/**
 * @author Sergey Evdokimov
 */
public class SpockMemberContributor extends NonCodeMembersContributor {

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    if (!(qualifierType instanceof PsiClassType)) return;

    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) return;

    PsiClass aClass = ((PsiClassType)qualifierType).resolve();
    if (!(aClass instanceof GrClassDefinition)) return; // Optimization: Spoc-test must be a Groovy class.

    if (!GroovyPsiManager.isInheritorCached(aClass, SpockUtils.SPEC_CLASS_NAME)) return;

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

}
