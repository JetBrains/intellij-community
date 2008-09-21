package org.jetbrains.plugins.groovy.refactoring.rename;

import com.intellij.refactoring.rename.RenameJavaVariableProcessor;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.Collection;
import java.util.Map;

/**
 * @author ilyas
 */
public class RenameGroovyPropertyProcessor extends RenameJavaVariableProcessor {

  @NotNull
  @Override
  public Collection<PsiReference> findReferences(final PsiElement element) {
    return super.findReferences(element);
  }

  @Override
  public boolean canProcessElement(final PsiElement element) {
    return element instanceof GrField;
  }

  @Override
  public void prepareRenaming(final PsiElement element, final String newName, final Map<PsiElement, String> allRenames) {
    super.prepareRenaming(element, newName, allRenames);
  }


}
