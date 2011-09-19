package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.NullableFunction;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrSafeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrTypeCastExpression;
import org.jetbrains.plugins.groovy.lang.psi.expectedTypes.GroovyExpectedTypesProvider;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

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

  @NotNull
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

        return GrExpression.EMPTY_ARRAY;
      }
    }
    return literal.getInitializers();
  }

  @NotNull
  private PsiType[] getCallArgumentTypes() {
    final GrExpression[] arguments = getCallArguments();
    return ContainerUtil.map2Array(arguments, PsiType.class, new NullableFunction<GrExpression, PsiType>() {
      @Override
      public PsiType fun(GrExpression grExpression) {
        return grExpression.getType();
      }
    });
  }

  @NotNull
  @Override
  public GroovyResolveResult[] multiResolve(boolean incompleteCode) {
    final PsiClassType.ClassResolveResult classResolveResult = myExpectedType.resolveGenerics();

    final GroovyResolveResult[] constructorCandidates =
      PsiUtil.getConstructorCandidates(myExpectedType, getCallArgumentTypes(), getElement());

    if (constructorCandidates.length == 0) {
      return new GroovyResolveResult[]{new GroovyResolveResultImpl(classResolveResult)};
    }
    return constructorCandidates;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return EMPTY_ARRAY;
  }
}
