package org.jetbrains.javafx.lang.psi.impl.types.java;

import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.types.JavaFxClassType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaClassTypeImpl extends JavaFxType implements JavaFxClassType {
  private final PsiClass myClass;

  public JavaClassTypeImpl(final PsiClass aClass) {
    myClass = aClass;
  }

  @Override
  public PsiElement getClassElement(final Project project) {
    return myClass;
  }

  @Override
  public String getPresentableText() {
    return myClass.getName();
  }

  @Override
  public String getCanonicalText() {
    return myClass.getQualifiedName();
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return myClass.getResolveScope();
  }
}
