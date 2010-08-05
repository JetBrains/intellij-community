package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class LiteralConstructorReference extends PsiReferenceBase.Poly<GrListOrMap> {
  private final PsiClassType myConstructedClass;

  public LiteralConstructorReference(@NotNull GrListOrMap element, @NotNull PsiClassType constructedClassType) {
    super(element, TextRange.from(0, 0), false);
    myConstructedClass = constructedClassType;
  }

  private static boolean isConstructorCall(PsiClassType expectedType,
                                           @Nullable PsiType[] argTypes,
                                           PsiMethod constructor,
                                           GroovyPsiElement context) {
    for (GroovyResolveResult candidate : PsiUtil.getConstructorCandidates(expectedType, argTypes, context)) {
      if (constructor.getManager().areElementsEquivalent(candidate.getElement(), constructor)) {
        return true;
      }
    }
    return false;
  }

  public static List<ResolveResult> getConstructorCandidates(PsiClassType classType,
                                                             @NotNull GroovyPsiElement context, @Nullable PsiType[] argTypes) {
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) return Collections.emptyList();

    List<ResolveResult> applicable = CollectionFactory.arrayList();
    final List<ResolveResult> byName = CollectionFactory.arrayList();
    for (PsiMethod constructor : psiClass.getConstructors()) {
      final ResolveResult resolveResult = new PsiElementResolveResult(constructor);
      byName.add(resolveResult);
      if (argTypes != null && isConstructorCall(classType, argTypes, constructor, context)) {
        applicable.add(resolveResult);
      }
    }
    if (applicable.isEmpty()) {
      applicable.addAll(byName);
    }
    return applicable;
  }

  @Nullable
  public static PsiClassType getTargetConversionType(@NotNull GrExpression expression) {
    //todo hack
    if (expression.getParent() instanceof GrSafeCastExpression) {
      final PsiType type = ((GrSafeCastExpression)expression.getParent()).getType();
      if (type instanceof PsiClassType) {
        return (PsiClassType)type;
      }
    }
    if (expression.getParent() instanceof GrTypeCastExpression) {
      final PsiType type = ((GrTypeCastExpression)expression.getParent()).getType();
      if (type instanceof PsiClassType) {
        return (PsiClassType)type;
      }
    }

    for (PsiType type : GroovyExpectedTypesProvider.getDefaultExpectedTypes(expression)) {
      if (type instanceof PsiClassType) {
        return (PsiClassType)type;
      }
    }
    return null;
  }

  @Nullable
  private PsiType[] argTypes() {
    final GrListOrMap literal = getElement();
    final PsiType listType = literal.getType();
    if (listType instanceof GrTupleType) {
      return ((GrTupleType)listType).getComponentTypes();
    }
    else if (listType instanceof GrMapType) {
      return PsiType.EMPTY_ARRAY;
    }
    return null;
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final GrListOrMap literal = getElement();
    final GrNamedArgument superConstructor = literal.findNamedArgument("super");
    if (superConstructor != null) {
      final PsiReference reference = ObjectUtils.assertNotNull(superConstructor.getLabel()).getReference();
      if (reference instanceof PsiPolyVariantReference) {
        return ((PsiPolyVariantReference)reference).multiResolve(incompleteCode);
      }
    }

    final List<ResolveResult> candidates = getConstructorCandidates(myConstructedClass, literal, argTypes());
    return candidates.toArray(new ResolveResult[candidates.size()]);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
