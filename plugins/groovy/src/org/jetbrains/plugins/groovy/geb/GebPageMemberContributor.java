package org.jetbrains.plugins.groovy.geb;

import com.intellij.psi.*;
import com.intellij.psi.scope.PsiScopeProcessor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrRenamableLightMethodBuilder;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyCommonClassNames;
import org.jetbrains.plugins.groovy.lang.resolve.NonCodeMembersContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.plugins.groovy.lang.resolve.processors.ClassHint;

import java.lang.reflect.Modifier;
import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class GebPageMemberContributor extends NonCodeMembersContributor {

  @Override
  protected String getParentClassName() {
    return "geb.Page";
  }

  @Override
  public void processDynamicElements(@NotNull PsiType qualifierType,
                                     PsiClass aClass,
                                     PsiScopeProcessor processor,
                                     GroovyPsiElement place,
                                     ResolveState state) {
    ClassHint classHint = processor.getHint(ClassHint.KEY);
    if (classHint != null && !classHint.shouldProcess(ClassHint.ResolveKind.PROPERTY)) return;

    String nameHint = ResolveUtil.getNameHint(processor);

    Map<String, PsiClass> supers = TypesUtil.getSuperClassesWithCache(aClass);

    PsiElement grCall = place.getParent();
    if (grCall instanceof GrMethodCall) {
      PsiElement grClosure = grCall.getParent();
      if (grClosure instanceof GrClosableBlock) {
        PsiElement contentField = grClosure.getParent();
        if (contentField instanceof GrField) {
          GrField f = (GrField)contentField;
          if ("content".equals(f.getName()) && f.hasModifierProperty(PsiModifier.STATIC) && f.getContainingClass() == aClass) {
            Map<String, PsiField> elements = GebUtil.getContentElements(aClass);
            for (PsiField field : elements.values()) {
              if (field.getNavigationElement() == place) {
                return; // Don't resolve variable definition.
              }
            }
          }
        }
      }
    }

    for (PsiClass psiClass : supers.values()) {
      Map<String,PsiField> contentFields = GebUtil.getContentElements(psiClass);

      if (nameHint == null) {
        for (Map.Entry<String, PsiField> entry : contentFields.entrySet()) {
          if (!processor.execute(entry.getValue(), state)) return;
        }
      }
      else {
        PsiField field = contentFields.get(nameHint);
        if (field != null) {
          processor.execute(field, state);
          return;
        }
      }
    }
  }
}
