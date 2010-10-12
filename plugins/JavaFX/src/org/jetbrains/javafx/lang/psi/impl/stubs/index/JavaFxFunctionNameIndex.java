package org.jetbrains.javafx.lang.psi.impl.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.javafx.lang.psi.JavaFxFunctionDefinition;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxFunctionNameIndex extends StringStubIndexExtension<JavaFxFunctionDefinition> {
  public static final StubIndexKey<String, JavaFxFunctionDefinition> KEY = StubIndexKey.createIndexKey("JFX.function.name");

  public StubIndexKey<String, JavaFxFunctionDefinition> getKey() {
    return KEY;
  }
}
