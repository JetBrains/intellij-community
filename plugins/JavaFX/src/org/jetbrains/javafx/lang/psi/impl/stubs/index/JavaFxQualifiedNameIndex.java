package org.jetbrains.javafx.lang.psi.impl.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.javafx.lang.psi.JavaFxQualifiedNamedElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxQualifiedNameIndex extends StringStubIndexExtension<JavaFxQualifiedNamedElement> {
  public static final StubIndexKey<String, JavaFxQualifiedNamedElement> KEY = StubIndexKey.createIndexKey("JFX.qualified.name");

  public StubIndexKey<String, JavaFxQualifiedNamedElement> getKey() {
    return KEY;
  }
}