package org.jetbrains.javafx.lang.psi.impl.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.javafx.lang.psi.JavaFxVariableDeclaration;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxVariableNameIndex extends StringStubIndexExtension<JavaFxVariableDeclaration> {
  public static final StubIndexKey<String,JavaFxVariableDeclaration> KEY = StubIndexKey.createIndexKey("JFX.variable.name");

  public StubIndexKey<String, JavaFxVariableDeclaration> getKey() {
    return KEY;
  }
}
