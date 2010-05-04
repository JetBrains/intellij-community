package org.jetbrains.plugins.groovy.lang.psi;

import com.intellij.pom.PomDeclarationSearcher;
import com.intellij.pom.PomTarget;
import com.intellij.psi.PsiElement;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author peter
 */
public class GroovyDeclarationSearcher extends PomDeclarationSearcher {
  @Override
  public void findDeclarationsAt(@NotNull PsiElement element, int offsetInElement, Consumer<PomTarget> consumer) {
    if (element instanceof GrTypeDefinition) {
      final PsiElement name = ((GrTypeDefinition)element).getNameIdentifierGroovy();
      if (name.getTextRange().shiftRight(-element.getTextRange().getStartOffset()).contains(offsetInElement)) {
        consumer.consume(GrClassSubstitutor.getSubstitutedClass((GrTypeDefinition)element));
      }
    }
  }
}
