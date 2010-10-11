package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.openapi.project.Project;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiElement;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.impl.JavaFxPsiManagerImpl;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxImportReference extends JavaFxPackageReference {
  private final boolean myOnDemand;

  public JavaFxImportReference(final JavaFxReferenceElement psiElement, final boolean onDemand) {
    super(psiElement);
    myOnDemand = onDemand;
  }

  @Override
  protected ResolveResult[] multiResolveInner(final boolean incompleteCode) {
    final String qualifiedName = myElement.getQualifiedName().toString();
    // find symbol
    if (!myOnDemand) {
      final PsiElement element = JavaFxPsiManagerImpl.getInstance(myElement.getProject()).getElementByQualifiedName(qualifiedName);
      if (element != null) {
        return JavaFxResolveUtil.createResolveResult(element);
      }
    }
    // find package
    ResolveResult[] resolveResults = super.multiResolveInner(incompleteCode);
    // TODO:
    // find file
    if (resolveResults.length == 0) {
      final Project project = myElement.getProject();
      final JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
      final PsiElement element = javaPsiFacade.findClass(qualifiedName, GlobalSearchScope.allScope(project));
      if (element != null) {
        resolveResults = JavaFxResolveUtil.createResolveResult(element);
      }
    }
    return resolveResults;
  }
}
