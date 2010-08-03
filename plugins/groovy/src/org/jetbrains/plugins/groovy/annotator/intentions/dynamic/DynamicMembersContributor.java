package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author peter
 */
public class DynamicMembersContributor extends NonCodeMembersContributor {
  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiScopeProcessor processor,
                                     PsiElement place,
                                     ResolveState state) {
    final DynamicManager manager = DynamicManager.getInstance(place.getProject());
    for (String qName : ResolveUtil.getAllSuperTypes(qualifierType, place).keySet()) {
      for (PsiMethod method : manager.getMethods(qName)) {
        if (!ResolveUtil.processElement(processor, method, state)) return;
      }

      for (PsiVariable var : manager.getProperties(qName)) {
        if (!ResolveUtil.processElement(processor, var, state)) return;
      }
    }
  }
}
