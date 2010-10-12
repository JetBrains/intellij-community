package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.psi.PsiPolyVariantReferenceBase;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.impl.PsiManagerEx;
import com.intellij.psi.impl.source.resolve.ResolveCache;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxReference extends PsiPolyVariantReferenceBase<JavaFxReferenceElement> {
  private static final ResolveCache.PolyVariantResolver<JavaFxReference> ourResolver =
    new ResolveCache.PolyVariantResolver<JavaFxReference>() {
      @Override
      public ResolveResult[] resolve(final JavaFxReference javaFxReference, final boolean incompleteCode) {
        return javaFxReference.multiResolveInner(incompleteCode);
      }
    };

  public JavaFxReference(final JavaFxReferenceElement psiElement) {
    super(psiElement, true);
  }

  @NotNull
  @Override
  public ResolveResult[] multiResolve(final boolean incompleteCode) {
    final ResolveResult[] results =
      ((PsiManagerEx)myElement.getManager()).getResolveCache().resolveWithCaching(this, ourResolver, true, incompleteCode);
    if (results.length == 0) {
      return ResolveResult.EMPTY_ARRAY;
    }
    return results;
  }

  protected ResolveResult[] multiResolveInner(final boolean incompleteCode) {
    final String name = myElement.getName();
    if (name != null) {
      final JavaFxResolveProcessor resolveProcessor = new JavaFxResolveProcessor(name);
      if (!JavaFxResolveUtil.treeWalkUp(myElement, resolveProcessor)) {
        return JavaFxResolveUtil.createResolveResult(resolveProcessor.getResult());
      }
    }
    return ResolveResult.EMPTY_ARRAY;
  }

  @NotNull
  @Override
  public Object[] getVariants() {
    return new Object[0];  //To change body of implemented methods use File | Settings | File Templates.
  }
}
