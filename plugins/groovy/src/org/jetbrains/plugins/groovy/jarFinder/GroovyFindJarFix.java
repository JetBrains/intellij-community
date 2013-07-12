package org.jetbrains.plugins.groovy.jarFinder;

import com.intellij.jarFinder.FindJarFix;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiQualifiedReference;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GrReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.imports.GrImportStatement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey Evdokimov
 */
public class GroovyFindJarFix extends FindJarFix<GrReferenceElement> {
  public GroovyFindJarFix(GrReferenceElement ref) {
    super(ref);
  }

  @Override
  protected Collection<String> getFqns(@NotNull GrReferenceElement ref) {
    GrImportStatement importStatement = PsiTreeUtil.getParentOfType(ref.getElement(), GrImportStatement.class);

    //from static imports
    if (importStatement != null) {
      GrCodeReferenceElement reference = importStatement.getImportReference();
      if (reference != null) {
        return Collections.singleton(reference.getText());
      }

      return Collections.emptyList();
    }

    if (ref.getQualifier() != null) return Collections.emptyList();

    final String className = ref.getReferenceName();
    if (className == null) return Collections.emptyList();

    PsiFile file = ref.getContainingFile().getOriginalFile();
    if (!(file instanceof GroovyFile)) return Collections.emptyList();

    GrImportStatement[] importList = ((GroovyFile)file).getImportStatements();

    for (GrImportStatement imp : importList) {
      if (className.equals(imp.getImportedName())) {
        GrCodeReferenceElement importReference = imp.getImportReference();
        if (importReference == null) return Collections.emptyList();
        return Collections.singleton(importReference.getText());
      }
    }

    return Collections.emptyList();
  }
}
