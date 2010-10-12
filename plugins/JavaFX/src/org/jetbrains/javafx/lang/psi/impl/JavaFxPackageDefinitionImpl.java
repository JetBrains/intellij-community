package org.jetbrains.javafx.lang.psi.impl;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxPackageDefinition;
import org.jetbrains.javafx.lang.psi.JavaFxReferenceElement;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxPackageDefinitionStub;

/**
 * Created by IntelliJ IDEA.
 * Author: Alexey.Ivanov
 * Date:   12.05.2010
 * Time:   16:21:57
 */
public class JavaFxPackageDefinitionImpl extends JavaFxBaseElementImpl<JavaFxPackageDefinitionStub> implements JavaFxPackageDefinition {
  public JavaFxPackageDefinitionImpl(@NotNull ASTNode node) {
    super(node);
  }

  public JavaFxPackageDefinitionImpl(JavaFxPackageDefinitionStub stub) {
    super(stub, JavaFxElementTypes.PACKAGE_DEFINITION);
  }

  // TODO: make better
  @Override
  public String getName() {
    final JavaFxPackageDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getName();
    }
    else {
      final JavaFxReferenceElement reference = (JavaFxReferenceElement)childToPsi(JavaFxElementTypes.REFERENCE_ELEMENT);
      if (reference == null) {
        return "";
      }
      return reference.getQualifiedName().toString();
    }
  }

  @Override
  public JavaFxQualifiedName getQualifiedName() {
    final JavaFxPackageDefinitionStub stub = getStub();
    if (stub != null) {
      return stub.getQualifiedName();
    }
    return JavaFxQualifiedName.fromString(getName());
  }
}
