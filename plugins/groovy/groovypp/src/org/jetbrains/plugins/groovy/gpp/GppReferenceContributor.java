package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;

/**
 * @author peter
 */
public class GppReferenceContributor extends PsiReferenceContributor {
  @Override
  public void registerReferenceProviders(PsiReferenceRegistrar registrar) {
    registrar.registerReferenceProvider(PlatformPatterns.psiElement(GrArgumentLabel.class), new PsiReferenceProvider() {
      @NotNull
      @Override
      public PsiReference[] getReferencesByElement(@NotNull PsiElement element, @NotNull ProcessingContext context) {
        final PsiElement parent = element.getParent();
        if (parent instanceof GrNamedArgument && parent.getParent() instanceof GrListOrMap) {
          return new PsiReference[]{new GppMapMemberReference(element)};
        }
        return PsiReference.EMPTY_ARRAY;
      }
    });
  }

  private static class GppMapMemberReference extends PsiReferenceBase<GrArgumentLabel> {

    public GppMapMemberReference(PsiElement element) {
      super((GrArgumentLabel)element);
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return element instanceof PsiMethod && super.isReferenceTo(element);
    }

    public PsiElement resolve() {
      final GrNamedArgument namedArgument = (GrNamedArgument) getElement().getParent();
      for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes((GrExpression)namedArgument.getParent())) {
        if (type instanceof PsiClassType) {
          final GrExpression value = namedArgument.getExpression();
          if (value != null && InheritanceUtil.isInheritor(value.getType(), GrClosableBlock.GROOVY_LANG_CLOSURE)) {
            final Pair<PsiMethod,PsiSubstitutor> method = GppClosureParameterTypeProvider.getOverriddenMethod(namedArgument);
            if (method != null) {
              return method.first;
            }
          } else {
            final PsiClass psiClass = ((PsiClassType)type).resolve();
            if (psiClass != null) {
              final String propertyName = getValue();
              final PsiMethod setter = PropertyUtil.findPropertySetter(psiClass, propertyName, false, true);
              if (setter != null) {
                return setter;
              }
              return PropertyUtil.findPropertyField(psiClass.getProject(), psiClass, propertyName, false);
            }
          }
        }
      }
      return null;
    }

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY; //todo
    }
  }
}
