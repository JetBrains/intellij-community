package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.types.JavaFxClassType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxClassTypeImpl extends JavaFxType implements JavaFxClassType {
  private final JavaFxClassDefinition myClass;

  protected JavaFxClassTypeImpl(final JavaFxClassDefinition aClass) {
    myClass = aClass;
  }

  @Override
  @Nullable
  public PsiElement getClassElement(final Project project) {
    return myClass;
  }

  @Override
  public String getPresentableText() {
    return myClass.getQualifiedName().getLastComponent();
  }

  @Override
  public String getCanonicalText() {
    return myClass.getQualifiedName().toString();
  }

  @Override
  public String getInternalCanonicalText() {
    return getCanonicalText();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myClass.getResolveScope();
  }
}
