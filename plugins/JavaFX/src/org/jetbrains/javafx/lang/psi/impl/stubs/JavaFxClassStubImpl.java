package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.parser.JavaFxElementTypes;
import org.jetbrains.javafx.lang.psi.JavaFxClassDefinition;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxClassStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public class JavaFxClassStubImpl extends JavaFxQualifiedNamedStubImpl<JavaFxClassDefinition> implements JavaFxClassStub {
  //private final JavaFxReferenceElement[] mySuperClasses;

  public JavaFxClassStubImpl(final StubElement parentStub, final JavaFxQualifiedName qualifiedName) {
    super(parentStub, JavaFxElementTypes.CLASS_DEFINITION, qualifiedName);
    //mySuperClasses = superClasses;
  }

  //public JavaFxReferenceElement[] getSuperClasses() {
  //  return mySuperClasses;
  //}
}
