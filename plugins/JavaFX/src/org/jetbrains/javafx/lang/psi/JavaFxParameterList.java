package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxParameterListStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:41:44
 */
public interface JavaFxParameterList extends JavaFxElement, StubBasedPsiElement<JavaFxParameterListStub> {
  @NotNull
  JavaFxParameter[] getParameters();
}
