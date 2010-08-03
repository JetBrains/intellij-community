package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.*;
import com.intellij.util.Processor;
import org.jetbrains.plugins.groovy.gpp.GppReferenceContributor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrMapType;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrTupleType;

/**
* @author peter
*/
public class LiteralConstructorSearcher {
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
      if (GppReferenceContributor.isConstructorCall(expectedType, ((GrTupleType)listType).getComponentTypes(), constructor, literal)) {
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
          final PsiReference reference = label.getReference();
          if (reference != null && reference.isReferenceTo(constructor)) {
            return consumer.process(reference);
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

  public boolean processLiteral(GrListOrMap list, PsiClassType expectedType) {
    return checkLiteralInstantiation(myConstructor, myConsumer, list, expectedType);
  }
}
