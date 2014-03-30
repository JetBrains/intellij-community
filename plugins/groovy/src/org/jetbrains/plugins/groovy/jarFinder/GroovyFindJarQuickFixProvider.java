package org.jetbrains.plugins.groovy.jarFinder;

import com.intellij.codeInsight.daemon.QuickFixActionRegistrar;
import com.intellij.codeInsight.quickfix.UnresolvedReferenceQuickFixProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Sergey Evdokimov
 */
public class GroovyFindJarQuickFixProvider extends UnresolvedReferenceQuickFixProvider<GrReferenceElement> {
  @Override
  public void registerFixes(@NotNull GrReferenceElement ref, @NotNull QuickFixActionRegistrar registrar) {
    registrar.register(new GroovyFindJarFix(ref));
  }

  @NotNull
  @Override
  public Class<GrReferenceElement> getReferenceClass() {
    return GrReferenceElement.class;
  }
}
