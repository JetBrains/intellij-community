package org.jetbrains.javafx.lang.psi;

import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxImportListStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:41:12
 */
public interface JavaFxImportList extends JavaFxElement, StubBasedPsiElement<JavaFxImportListStub> {
  JavaFxImportList[] EMPTY_ARRAY = new JavaFxImportList[0];
  @NotNull
  JavaFxImportStatement[] getImportStatements();
}
