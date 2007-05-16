package org.jetbrains.plugins.groovy.lang.resolve.processors;

import com.intellij.psi.*;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyResolveResultImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrApplicationExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.path.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

/**
 * @author ven
 * Resolves methods from method call or function application.
 */
public class MethodResolverProcessor extends ResolverProcessor {
  PsiType[] myArgumentTypes;

  private List<GroovyResolveResult> myInapplicableCandidates = new ArrayList<GroovyResolveResult>();


  public MethodResolverProcessor(String name, GroovyPsiElement place) {
    super(name, EnumSet.of(ResolveKind.METHOD, ResolveKind.PROPERTY), place);
    myArgumentTypes = getArgumentTypes(place);
  }

  public boolean execute(PsiElement element, PsiSubstitutor substitutor) {
    if (element instanceof PsiMethod) {
      PsiMethod method = (PsiMethod) element;
      if (method.isConstructor()) return true; //not interested in constructors <now>

      boolean isAccessible = isAccessible((PsiNamedElement) element);
      boolean isApplicable = isApplicable(method);
      if (isApplicable) {
        myCandidates.add(new GroovyResolveResultImpl(method, isAccessible));
      }
      else {
        myInapplicableCandidates.add(new GroovyResolveResultImpl(method, isAccessible));
      }

      return true;
    } else {
      return super.execute(element, substitutor);
    }
  }

  public GroovyResolveResult[] getCandidates() {
    return myCandidates.size() > 0 ? super.getCandidates() :
        myInapplicableCandidates.toArray(new GroovyResolveResult[myInapplicableCandidates.size()]);
  }

  private boolean isApplicable(PsiMethod method) {
    PsiParameter[] parameters = method.getParameterList().getParameters();
    if (parameters.length != myArgumentTypes.length) return false;

    for (int i = 0; i < parameters.length; i++) {
      PsiType argType = myArgumentTypes[i];
      PsiType parameterType = parameters[i].getType();
      if (!parameterType.isAssignableFrom(argType)) return false;
    }

    return true;
  }


  private static PsiType[] getArgumentTypes(GroovyPsiElement place) {
    PsiElementFactory factory = place.getManager().getElementFactory();
    PsiElement parent = place.getParent();
    if (parent instanceof GrMethodCall) {
      List<PsiType> result = new ArrayList<PsiType>();
      GrMethodCall methodCall = (GrMethodCall) parent;
      GrNamedArgument[] namedArgs = methodCall.getNamedArguments();
      if (namedArgs.length > 0) {
        result.add(factory.createTypeByFQClassName("java.util.HashMap", place.getResolveScope()));
      }
      GrExpression[] expressions = methodCall.getExpressionArguments();
      for (GrExpression expression : expressions) {
        PsiType type = expression.getType();
        if (type == null) {
          result.add(PsiType.NULL);
        } else {
          result.add(type);
        }
      }
      return result.toArray(new PsiType[result.size()]);

    } else if (parent instanceof GrApplicationExpression) {
      GrExpression[] args = ((GrApplicationExpression) parent).getArguments();
      PsiType[] result = new PsiType[args.length];
      for (int i = 0; i < result.length; i++) {
        PsiType argType = args[i].getType();
        if (argType == null) {
          result[i] = PsiType.NULL;
        } else {
          result[i] = argType;
        }
      }

      return result;
    }

    return PsiType.EMPTY_ARRAY;
  }
}
