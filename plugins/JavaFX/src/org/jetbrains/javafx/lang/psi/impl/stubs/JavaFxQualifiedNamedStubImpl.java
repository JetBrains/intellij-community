package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.javafx.lang.psi.JavaFxQualifiedNamedElement;
import org.jetbrains.javafx.lang.psi.impl.JavaFxQualifiedName;
import org.jetbrains.javafx.lang.psi.stubs.JavaFxQualifiedNamedStub;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxQualifiedNamedStubImpl<T extends JavaFxQualifiedNamedElement> extends StubBase<T>
  implements JavaFxQualifiedNamedStub<T> {
  private final JavaFxQualifiedName myQualifiedName;

  public JavaFxQualifiedNamedStubImpl(final StubElement parent,
                                      final IStubElementType elementType,
                                      final JavaFxQualifiedName qualifiedName) {
    super(parent, elementType);
    myQualifiedName = qualifiedName;
  }

  public String getName() {
    return myQualifiedName.getLastComponent();
  }

  @Override
  public JavaFxQualifiedName getQualifiedName() {
    return myQualifiedName;
  }
}
