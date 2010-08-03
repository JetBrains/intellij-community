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
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrClosureType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * @author peter
 */
public class GppReferenceContributor extends PsiReferenceContributor {
  public static boolean mayInvokeConstructor(PsiClassType expectedType, PsiMethod constructor, GrExpression args) {
    if (args == null) {
      return true;
    }

    final PsiType type = args.getType();
    if (type == null) {
      return true;
    }

    if (type instanceof GrTupleType) {
      return isConstructorCall(expectedType, ((GrTupleType)type).getComponentTypes(), constructor, args);
    }

    return isConstructorCall(expectedType, new PsiType[]{type}, constructor, args);
  }

  public static boolean isConstructorCall(PsiClassType expectedType,
                                           PsiType[] argTypes,
                                           PsiMethod constructor,
                                           GroovyPsiElement context) {
    for (GroovyResolveResult candidate : PsiUtil.getConstructorCandidates(expectedType, argTypes, context)) {
      if (constructor.getManager().areElementsEquivalent(candidate.getElement(), constructor)) {
        return true;
      }
    }
    return false;
  }

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

  private static class GppMapMemberReference extends PsiReferenceBase.Poly<GrArgumentLabel> {

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
      for (PsiType type : getTargetConversionTypes((GrExpression)namedArgument.getParent())) {
        if (type instanceof PsiClassType) {
          final PsiClassType classType = (PsiClassType)type;
          final PsiClass psiClass = classType.resolve();
          if (psiClass != null) {
            final GrExpression value = namedArgument.getExpression();

            final List<ResolveResult> applicable = addMethodCandidates(classType, value);

            final String memberName = getValue();
            if ("super".equals(memberName) && GppTypeConverter.hasTypedContext(myElement)) {
              applicable.addAll(addConstructorCandidates(classType, psiClass, value));
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
      }
      return ResolveResult.EMPTY_ARRAY;
    }

    private static Set<PsiType> getTargetConversionTypes(GrExpression expression) {
      //todo hack
      if (expression.getParent() instanceof GrSafeCastExpression) {
        final PsiType type = ((GrSafeCastExpression)expression.getParent()).getType();
        if (type != null) {
          return Collections.singleton(type);
        }
      }

      return GroovyExpectedTypesProvider.getDefaultExpectedTypes(expression);
    }


    private static List<ResolveResult> addConstructorCandidates(PsiClassType classType, PsiClass psiClass, GrExpression value) {
      List<ResolveResult> applicable = CollectionFactory.arrayList();
      final List<ResolveResult> byName = CollectionFactory.arrayList();
      for (PsiMethod constructor : psiClass.getConstructors()) {
        final ResolveResult resolveResult = new PsiElementResolveResult(constructor);
        byName.add(resolveResult);
        if (mayInvokeConstructor(classType, constructor, value)) {
          applicable.add(resolveResult);
        }
      }
      if (applicable.isEmpty()) {
        applicable.addAll(byName);
      }
      return applicable;
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
