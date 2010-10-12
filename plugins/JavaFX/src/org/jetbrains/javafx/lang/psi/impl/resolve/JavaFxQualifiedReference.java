package org.jetbrains.javafx.lang.psi.impl.resolve;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.ResolveResult;
import com.intellij.psi.ResolveState;
import com.intellij.psi.impl.source.PsiClassReferenceType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxCallExpression;
import org.jetbrains.javafx.lang.psi.JavaFxExpression;
import org.jetbrains.javafx.lang.psi.JavaFxIndexExpression;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.impl.JavaFxPsiManagerImpl;
import org.jetbrains.javafx.lang.psi.types.JavaFxClassType;
import org.jetbrains.javafx.lang.psi.types.JavaFxFileType;
import org.jetbrains.javafx.lang.psi.types.JavaFxFunctionType;
import org.jetbrains.javafx.lang.psi.types.JavaFxSequenceType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxQualifiedReference extends JavaFxReference {
  private final JavaFxExpression myQualifier;

  public JavaFxQualifiedReference(final JavaFxReferenceElement psiElement, @NotNull final JavaFxExpression qualifier) {
    super(psiElement);
    myQualifier = qualifier;
  }

  @Override
  protected ResolveResult[] multiResolveInner(boolean incompleteCode) {
    final String name = myElement.getName();
    final JavaFxResolveProcessor resolveProcessor = new JavaFxResolveProcessor(name);
    PsiType type = myQualifier.getType();
    if (type instanceof JavaFxFunctionType && myQualifier instanceof JavaFxCallExpression) {
      type = ((JavaFxFunctionType)type).getReturnType();
    }
    if (type instanceof JavaFxSequenceType && myQualifier instanceof JavaFxIndexExpression) {
      type = ((JavaFxSequenceType)type).getElementType();
    }
    if (type instanceof JavaFxClassType) {
      final PsiElement classElement = ((JavaFxClassType)type).getClassElement(myElement.getProject());
      if (processClass(resolveProcessor, classElement)) {
        return JavaFxResolveUtil.createResolveResult(resolveProcessor.getResult());
      }
    }
    if (type instanceof PsiClassReferenceType) {
      if (processClass(resolveProcessor, ((PsiClassReferenceType)type).resolve())) {
        return JavaFxResolveUtil.createResolveResult(resolveProcessor.getResult());
      }
    }
    if (type instanceof JavaFxFileType) {
      if (!((JavaFxFileType)type).getFile().processDeclarations(resolveProcessor, ResolveState.initial(), null, myElement)) {
        return JavaFxResolveUtil.createResolveResult(resolveProcessor.getResult());
      }
    }
    final PsiElement element = JavaFxPsiManagerImpl.getInstance(myElement.getProject()).getElementByQualifiedName(myQualifier.getText());
    if (element != null && !element.processDeclarations(resolveProcessor, ResolveState.initial(), null, myElement)) {
      return JavaFxResolveUtil.createResolveResult(resolveProcessor.getResult());
    }
    return ResolveResult.EMPTY_ARRAY;
  }

  private boolean processClass(@NotNull final JavaFxResolveProcessor resolveProcessor, @Nullable final PsiElement classElement) {
    if (classElement != null) {
      if (!classElement.processDeclarations(resolveProcessor, ResolveState.initial(), null, myElement)) {
        return true;
      }
    }
    return false;
  }
}
