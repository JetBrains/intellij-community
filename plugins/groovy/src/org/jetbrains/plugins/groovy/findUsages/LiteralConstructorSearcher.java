package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.util.ObjectUtils;
import com.intellij.util.Processor;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.GrListOrMap;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;

/**
* @author peter
*/
public class LiteralConstructorSearcher {
  private final PsiMethod myConstructor;
  private final Processor<PsiReference> myConsumer;
  private final boolean myIncludeOverloads;

  public LiteralConstructorSearcher(PsiMethod constructor, Processor<PsiReference> consumer, boolean includeOverloads) {
    myConstructor = constructor;
    myConsumer = consumer;
    myIncludeOverloads = includeOverloads;
  }

  public boolean processLiteral(GrListOrMap literal, PsiClassType expectedType) {
    if (literal.isMap()) {
      final GrNamedArgument argument = literal.findNamedArgument("super");
      if (argument != null) {
        return processConstructorReference(ObjectUtils.assertNotNull(argument.getLabel()).getReference());
      }
    }

    return processConstructorReference(new LiteralConstructorReference(literal, expectedType));
  }

  private boolean processConstructorReference(@Nullable PsiReference reference) {
    if (reference != null && (myIncludeOverloads || reference.isReferenceTo(myConstructor))) {
    return myConsumer.process(reference);
  }
    return true;
  }
}
