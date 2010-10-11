package org.jetbrains.javafx.lang.psi.stubs;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.JavaFxLanguage;
import org.jetbrains.javafx.lang.psi.JavaFxElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxStubElementType<StubT extends StubElement, PsiT extends JavaFxElement> extends IStubElementType<StubT, PsiT> {
  public JavaFxStubElementType(@NotNull @NonNls final String debugName) {
    super(debugName, JavaFxLanguage.getInstance());
  }

  public abstract PsiElement createElement(ASTNode node);

  @Override
  public String toString() {
    return "JavaFx:" + super.toString();
  }

  public String getExternalId() {
    return "jfx." + super.toString();
  }
}
