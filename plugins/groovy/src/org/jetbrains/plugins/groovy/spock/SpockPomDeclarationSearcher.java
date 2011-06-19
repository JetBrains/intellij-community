package org.jetbrains.plugins.groovy.spock;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import java.util.Map;

/**
 * @author Sergey Evdokimov
 */
public class SpockPomDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    String name = SpockUtils.getNameByReference(element);
    if (name == null) return;

    GrMethod method = PsiTreeUtil.getParentOfType(element, GrMethod.class);
    if (method == null) return;

    PsiClass containingClass = method.getContainingClass();
    if (containingClass == null) return;

    if (!GroovyPsiManager.isInheritorCached(containingClass, SpockUtils.SPEC_CLASS_NAME)) return;

    Map<String, SpockVariableDescriptor> cachedValue = SpockUtils.getVariableMap(method);

    SpockVariableDescriptor descriptor = cachedValue.get(name);
    if (descriptor == null) return;

    if (descriptor.getNavigationElement() == element) {
      consumer.consume(descriptor.getVariable());
    }
  }
}
