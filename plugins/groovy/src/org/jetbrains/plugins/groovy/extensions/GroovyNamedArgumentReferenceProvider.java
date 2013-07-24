package org.jetbrains.plugins.groovy.extensions;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;

/**
 * @author Sergey Evdokimov
 */
public interface GroovyNamedArgumentReferenceProvider {

  PsiReference[] createRef(@NotNull PsiElement element,
                           @NotNull GrNamedArgument namedArgument,
                           @NotNull GroovyResolveResult resolveResult,
                           @NotNull ProcessingContext context);
}
