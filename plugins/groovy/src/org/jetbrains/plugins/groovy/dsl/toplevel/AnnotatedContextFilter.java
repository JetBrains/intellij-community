package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifierList;
import com.intellij.psi.PsiModifierListOwner;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;

/**
 * @author peter
 */
public class AnnotatedContextFilter implements ContextFilter {

  private final String myAnnoQName;

  public AnnotatedContextFilter(String annoQName) {
    myAnnoQName = annoQName;
  }

  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    return hasAnnotatedContext(descriptor.getPlace(), myAnnoQName);
  }

  public static boolean hasAnnotatedContext(@NotNull PsiElement context, String annoQName) {
    PsiElement current = context;
    while (current != null) {
      if (current instanceof PsiModifierListOwner && !(current instanceof GrVariableDeclaration) && hasAnnotation(((PsiModifierListOwner)current).getModifierList(), annoQName)) {
        return true;
      }

      if (current instanceof PsiFile) {
        if (current instanceof GroovyFile) {
          final GrPackageDefinition packageDefinition = ((GroovyFile)current).getPackageDefinition();
          if (packageDefinition != null && hasAnnotation(packageDefinition.getAnnotationList(), annoQName)) {
            return true;
          }
        }
        return false;
      }

      current = current.getContext();
    }

    return false;
  }

  private static boolean hasAnnotation(PsiModifierList modifierList, String annoQName) {
    return modifierList != null && modifierList.findAnnotation(annoQName) != null;
  }



}