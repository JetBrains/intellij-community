package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.types.JavaFxClassType;
import org.jetbrains.javafx.lang.psi.types.JavaFxSequenceType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxSequenceTypeImpl extends JavaFxType implements JavaFxSequenceType, JavaFxClassType {
  private final PsiType myElementType;

  JavaFxSequenceTypeImpl(final PsiType elementType) {
    myElementType = elementType;
  }

  @Override
  public PsiType getElementType() {
    return myElementType;
  }

  @Override
  public String getPresentableText() {
    return StringUtil.joinOrNull(myElementType.getPresentableText(), "[]");
  }

  @Override
  public String getCanonicalText() {
    return StringUtil.joinOrNull(myElementType.getCanonicalText(), "[]");
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myElementType.getResolveScope();
  }

  @Override
  public PsiElement getClassElement(final Project project) {
    return JavaFxTypeUtil.getSequenceClassType(project).getClassElement(project);
  }
}
