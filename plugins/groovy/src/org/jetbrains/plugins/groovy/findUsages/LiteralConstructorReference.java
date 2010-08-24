package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.psi.util.InheritanceUtil;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.CollectionFactory;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.gpp.GppTypeConverter;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class LiteralConstructorReference extends PsiReferenceBase.Poly<GrListOrMap> {
  private final PsiClassType myExpectedType;

  public LiteralConstructorReference(@NotNull GrListOrMap element, @NotNull PsiClassType constructedClassType) {
    super(element, TextRange.from(0, 0), false);
    myExpectedType = constructedClassType;
  }

  @Override
  public PsiElement handleElementRename(String newElementName) throws IncorrectOperationException {
    return getElement();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    return getElement();
  }

  public PsiClassType getConstructedClassType() {
    return myExpectedType;
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

  private static List<ResolveResult> getConstructorCandidates(PsiClassType classType,
                                                             @NotNull GroovyPsiElement context, @Nullable PsiType[] argTypes, boolean exactMatchesOnly) {
    PsiClass psiClass = classType.resolve();
    if (psiClass == null) return Collections.emptyList();

    final PsiMethod[] constructors = psiClass.getConstructors();
    if (constructors.length == 0) {
      return Arrays.<ResolveResult>asList(new PsiElementResolveResult(psiClass));
    }

    List<ResolveResult> applicable = CollectionFactory.arrayList();
    final List<ResolveResult> byName = CollectionFactory.arrayList();
    for (PsiMethod constructor : constructors) {
      final ResolveResult resolveResult = new PsiElementResolveResult(constructor);
      byName.add(resolveResult);
      if (argTypes != null && isConstructorCall(classType, argTypes, constructor, context)) {
        applicable.add(resolveResult);
      }
    }

    if (!exactMatchesOnly && applicable.isEmpty()) {
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
        final String text = type.getCanonicalText();
        if (!CommonClassNames.JAVA_LANG_OBJECT.equals(text) &&
            !CommonClassNames.JAVA_LANG_STRING.equals(text)) {
          return (PsiClassType)type;
        }
      }
    }
    return null;
  }

  @Nullable
  public GrExpression[] getCallArguments() {
    final GrListOrMap literal = getElement();
    if (literal.isMap()) {
      final GrNamedArgument argument = literal.findNamedArgument("super");
      if (argument != null) {
        final GrExpression expression = argument.getExpression();
        if (expression instanceof GrListOrMap && !((GrListOrMap)expression).isMap()) {
          return ((GrListOrMap)expression).getInitializers();
        }
        if (expression != null) {
          return new GrExpression[]{expression};
        }

        return null;
      }
    }
    return literal.getInitializers();
  }

  @Nullable
  private PsiType[] getCallArgumentTypes() {
    final GrExpression[] arguments = getCallArguments();
    if (arguments == null) return null;
    return ContainerUtil.map2Array(arguments, PsiType.class, new NullableFunction<GrExpression, PsiType>() {
      @Override
      public PsiType fun(GrExpression grExpression) {
        return grExpression.getType();
      }
    });
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(boolean incompleteCode) {
    final List<ResolveResult> candidates = getConstructorCandidates(myExpectedType, getElement(), getCallArgumentTypes(), false);
    return candidates.toArray(new ResolveResult[candidates.size()]);
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }

  public boolean isConstructorCallCorrect() {
    final boolean isMap = getElement().isMap();
    if (!isMap && InheritanceUtil.isInheritor(myExpectedType, CommonClassNames.JAVA_LANG_ITERABLE) ||
        isMap && InheritanceUtil.isInheritor(myExpectedType, CommonClassNames.JAVA_UTIL_MAP)) {
      return GppTypeConverter.hasDefaultConstructor(myExpectedType);
    }

    return resolve() != null;
  }
}
