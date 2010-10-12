package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxPackageDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxPackageDefinitionStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxPackageDefinitionStubImpl extends JavaFxQualifiedNamedStubImpl<JavaFxPackageDefinition>
  implements JavaFxPackageDefinitionStub {

  public JavaFxPackageDefinitionStubImpl(final StubElement parentStub, final JavaFxQualifiedName packageName) {
    super(parentStub, JavaFxElementTypes.PACKAGE_DEFINITION, packageName);
  }
}
