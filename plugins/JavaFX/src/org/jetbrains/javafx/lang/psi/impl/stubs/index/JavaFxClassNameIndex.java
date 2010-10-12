package org.jetbrains.javafx.lang.psi.impl.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxClassNameIndex extends StringStubIndexExtension<JavaFxClassDefinition> {
  public static final StubIndexKey<String,JavaFxClassDefinition> KEY = StubIndexKey.createIndexKey("JFX.class.shortName");

  public StubIndexKey<String, JavaFxClassDefinition> getKey() {
    return KEY;
  }
}
