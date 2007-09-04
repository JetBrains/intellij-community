package org.jetbrains.plugins.groovy.findUsages;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiReference;
import com.intellij.psi.search.searches.ReferencesSearch;
import com.intellij.psi.search.searches.DirectClassInheritorsSearch;
import com.intellij.util.Processor;
import com.intellij.util.QueryExecutor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrConstructorInvocation;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrNewExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author ven
 */
public class ConstructorReferencesSearcher implements QueryExecutor<PsiReference, ReferencesSearch.SearchParameters> {
  public boolean execute(ReferencesSearch.SearchParameters queryParameters, final Processor<PsiReference> consumer) {
    final PsiElement element = queryParameters.getElementToSearch();
    if (element instanceof PsiMethod) {
      final PsiMethod method = (PsiMethod) element;
      if (method.isConstructor()) {
        final PsiClass clazz = method.getContainingClass();
        ReferencesSearch.search(clazz).forEach(new Processor<PsiReference>() {
          public boolean process(PsiReference ref) {
            final PsiElement element = ref.getElement();
            if (element instanceof GrCodeReferenceElement &&
                element.getParent() instanceof GrNewExpression) {
              final GrNewExpression newExpression = (GrNewExpression) element.getParent();
              final PsiMethod constructor = newExpression.resolveConstructor();
              if (constructor != null &&
                  constructor.getManager().areElementsEquivalent(constructor, method)) {
                if (!consumer.process(ref)) return false;
              }
            }
            return true;
          }
        });

        //this()
        if (clazz instanceof GrTypeDefinition) {
          if (!ApplicationManager.getApplication().runReadAction(new Computable<Boolean>() {
            public Boolean compute() {
              return processClassConstructors(clazz, method, consumer, true);
            }
          })) return false;
        }

        //super  : does not work now, need to invent a way for it to work without repository
        if (!DirectClassInheritorsSearch.search(clazz).forEach(new Processor<PsiClass>() {
          public boolean process(PsiClass inheritor) {
            if (inheritor instanceof GrTypeDefinition) {
              if (!processClassConstructors(inheritor, method, consumer, false)) return false;
            }

            return true;
          }
        })) return false;

      }

    }

    return true;
  }

  private boolean processClassConstructors(PsiClass clazz, PsiMethod method, Processor<PsiReference> consumer, boolean processThisRefs) {
    final PsiMethod[] constructors = clazz.getConstructors();
    for (PsiMethod constructor : constructors) {
      final GrOpenBlock block = ((GrMethod) constructor).getBlock();
      if (block != null) {
        final GrStatement[] statements = block.getStatements();
        if (statements.length > 0 && statements[0] instanceof GrConstructorInvocation) {
          final GrConstructorInvocation invocation = (GrConstructorInvocation) statements[0];
          if (invocation.isThisCall() == processThisRefs &&
              invocation.getManager().areElementsEquivalent(invocation.resolveConstructor(), method)) {
            if (!consumer.process(invocation)) return false;
          }
        }
      }
    }

    return true;
  }
}
