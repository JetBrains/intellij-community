package org.jetbrains.plugins.groovy.gpp;

import com.intellij.openapi.util.Pair;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.psi.util.PropertyUtil;
import com.intellij.util.ArrayUtil;
import com.intellij.util.ProcessingContext;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.findUsages.LiteralConstructorReference;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;

import java.util.List;

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

  public static class GppMapMemberReference extends PsiReferenceBase.Poly<GrArgumentLabel> {

    public GppMapMemberReference(PsiElement element) {
      super((GrArgumentLabel)element);
    }

    @Override
    public boolean isReferenceTo(PsiElement element) {
      return element instanceof PsiMethod && super.isReferenceTo(element);
    }

    @NotNull
    @Override
    public ResolveResult[] multiResolve(boolean incompleteCode) {
      final GrArgumentLabel context = getElement();
      final GrNamedArgument namedArgument = (GrNamedArgument) context.getParent();
      final GrExpression map = (GrExpression)namedArgument.getParent();
      final PsiClassType classType = LiteralConstructorReference.getTargetConversionType(map);
      if (classType != null) {
        final PsiClass psiClass = classType.resolve();
        if (psiClass != null) {
          final GrExpression value = namedArgument.getExpression();

          final List<ResolveResult> applicable = addMethodCandidates(classType, value);

          final String memberName = getValue();
          if ("super".equals(memberName) && GppTypeConverter.hasTypedContext(myElement)) {
            final LiteralConstructorReference reference = (LiteralConstructorReference)map.getReference();
            if (reference != null) {
              return reference.multiResolve(incompleteCode);
            }
          }

          if (value == null || applicable.isEmpty()) {
            final PsiMethod setter = PropertyUtil.findPropertySetter(psiClass, memberName, false, true);
            if (setter != null) {
              applicable.add(new PsiElementResolveResult(setter));
            } else {
              final PsiField field = PropertyUtil.findPropertyField(psiClass.getProject(), psiClass, memberName, false);
              if (field != null) {
                applicable.add(new PsiElementResolveResult(field));
              }
            }
          }

          return applicable.toArray(new ResolveResult[applicable.size()]);
        }
      }

      return ResolveResult.EMPTY_ARRAY;
    }


    private List<ResolveResult> addMethodCandidates(PsiClassType classType, GrExpression value) {
      PsiType valueType = value == null ? null : value.getType();
      final List<ResolveResult> applicable = CollectionFactory.arrayList();

      if (value == null || InheritanceUtil.isInheritor(valueType, GrClosableBlock.GROOVY_LANG_CLOSURE)) {
        final List<ResolveResult> byName = CollectionFactory.arrayList();
        for (Pair<PsiMethod, PsiSubstitutor> variant : GppClosureParameterTypeProvider.getMethodsToOverrideImplementInInheritor(classType, false)) {
          final PsiMethod method = variant.first;
          if (getValue().equals(method.getName())) {
            final ResolveResult resolveResult = new PsiElementResolveResult(method);
            byName.add(resolveResult);
            if (valueType instanceof GrClosureType) {
              final PsiType[] psiTypes = GppClosureParameterTypeProvider.getParameterTypes(variant);
              if (GppTypeConverter.isClosureOverride(psiTypes, (GrClosureType)valueType, myElement)) {
                applicable.add(resolveResult);
              }
            }
          }
        }

        if (applicable.isEmpty()) {
          return byName;
        }
      }
      return applicable;
    }

    @NotNull
    public Object[] getVariants() {
      return ArrayUtil.EMPTY_OBJECT_ARRAY; //todo
    }
  }
}
