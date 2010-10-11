package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiElement;
import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.impl.JavaFxPsiManagerImpl;
import org.jetbrains.javafx.lang.psi.types.JavaFxClassType;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPrimitiveType extends JavaFxType implements JavaFxClassType {
  public static final JavaFxPrimitiveType STRING = new JavaFxPrimitiveType("java.lang.String");
  public static final JavaFxPrimitiveType INTEGER = new JavaFxPrimitiveType("java.lang.Integer");
  public static final JavaFxPrimitiveType NUMBER = new JavaFxPrimitiveType("java.lang.Double");
  public static final JavaFxPrimitiveType BOOLEAN = new JavaFxPrimitiveType("java.lang.Boolean");
  public static final JavaFxPrimitiveType DURATION = new JavaFxPrimitiveType("javafx.lang.Duration");

  private final String myQualifiedName;

  private JavaFxPrimitiveType(final String qualifiedName) {
    myQualifiedName = qualifiedName;
  }

  @Override
  public PsiElement getClassElement(final Project project) {
    return JavaFxPsiManagerImpl.getInstance(project).getElementByQualifiedName(myQualifiedName);
  }

  @Override
  public String getPresentableText() {
    return StringUtil.getShortName(myQualifiedName);
  }

  @Override
  public String getCanonicalText() {
    return myQualifiedName;
  }

  @Override
  public GlobalSearchScope getResolveScope() {
    return GlobalSearchScope.EMPTY_SCOPE;
  }
}
