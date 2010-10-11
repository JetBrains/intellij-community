package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public interface JavaFxTypeElement extends JavaFxElement {
  @NotNull
  PsiType getType();
}
