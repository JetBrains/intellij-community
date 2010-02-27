package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.psi.PsiElement;
import com.intellij.util.ProcessingContext;

/**
 * @author peter
 */
public interface ContextFilter {
  boolean isApplicable(PsiElement place, String fqName, ProcessingContext ctx);


}
