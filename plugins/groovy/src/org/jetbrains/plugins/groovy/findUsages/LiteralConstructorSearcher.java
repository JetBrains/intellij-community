package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;
import org.jetbrains.plugins.groovy.lang.psi.util.PsiUtil;

/**
* @author peter
*/
class LiteralConstructorSearcher {
  private final PsiMethod myConstructor;
  private final Processor<PsiReference> myConsumer;

  public LiteralConstructorSearcher(PsiMethod constructor, Processor<PsiReference> consumer) {
    myConstructor = constructor;
    myConsumer = consumer;
  }

  private static boolean checkLiteralInstantiation(PsiMethod constructor,
                                                Processor<PsiReference> consumer,
                                                GrListOrMap literal,
                                                PsiClassType expectedType) {
    final PsiType listType = literal.getType();
    if (listType instanceof GrTupleType) {
      if (isConstructorCall(expectedType, ((GrTupleType)listType).getComponentTypes(), constructor, literal)) {
        return consumer.process(PsiReferenceBase.createSelfReference(literal, TextRange.from(0, literal.getTextLength()), constructor));
      }
    }
    else if (listType instanceof GrMapType) {
      final PsiType constructorArgs = ((GrMapType)listType).getValueType("super");
      if (constructorArgs == null) {
        if (constructor.getParameterList().getParametersCount() == 0) {
          if (!consumer.process(PsiReferenceBase.createSelfReference(literal, TextRange.from(0, literal.getTextLength()), constructor))) {
            return false;
          }
        }
        return true;
      }

      for (GrNamedArgument argument : literal.getNamedArguments()) {
        final GrArgumentLabel label = argument.getLabel();
        if (label != null && "super".equals(label.getName())) {
          if (mayInvokeConstructor(expectedType, constructor, argument.getExpression())) {
            return consumer.process(PsiReferenceBase.createSelfReference(label, TextRange.from(0, label.getTextLength()), constructor));
          }
          return true;
        }
      }

      //no 'super', only default constructor applicable
      if (constructor.getParameterList().getParametersCount() == 0) {
        return consumer.process(PsiReferenceBase.createSelfReference(literal, TextRange.from(0, literal.getTextLength()), constructor));
      }
    }
    return true;
  }

  private static boolean mayInvokeConstructor(PsiClassType expectedType, PsiMethod constructor, GrExpression args) {
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

  private static boolean isConstructorCall(PsiClassType expectedType,
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

  public boolean processLiteral(GrListOrMap list, PsiClassType expectedType) {
    return checkLiteralInstantiation(myConstructor, myConsumer, list, expectedType);
  }
}
