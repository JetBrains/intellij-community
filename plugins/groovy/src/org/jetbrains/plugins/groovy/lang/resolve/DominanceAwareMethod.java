package org.jetbrains.plugins.groovy.lang.resolve;

import com.intellij.psi.PsiMethod;
import com.intellij.psi.PsiSubstitutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;

/**
 * @author peter
 */
public interface DominanceAwareMethod extends PsiMethod {

  boolean isMoreConcreteThan(@NotNull PsiSubstitutor substitutor,
                    @NotNull PsiMethod another, @NotNull PsiSubstitutor anotherSubstitutor,
                    @NotNull GroovyPsiElement context);

}
