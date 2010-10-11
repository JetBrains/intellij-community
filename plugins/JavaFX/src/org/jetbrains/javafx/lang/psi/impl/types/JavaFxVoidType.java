package org.jetbrains.javafx.lang.psi.impl.types;

import com.intellij.psi.search.GlobalSearchScope;
import org.jetbrains.javafx.lang.psi.types.JavaFxType;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxVoidType extends JavaFxType {
  public static final JavaFxVoidType INSTANCE = new JavaFxVoidType();

  private JavaFxVoidType() {
  }

  @Override
  public String getPresentableText() {
    return "Void";
  }

  @Override
  public String getCanonicalText() {
    return "Void";
  }

  // TODO:
  @Override
  public GlobalSearchScope getResolveScope() {
    return null;
  }
}
