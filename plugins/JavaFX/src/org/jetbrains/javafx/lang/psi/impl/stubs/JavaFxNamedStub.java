package org.jetbrains.javafx.lang.psi.impl.stubs;

import com.intellij.psi.PsiNamedElement;
import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.NamedStub;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;

/**
 * Created by IntelliJ IDEA.
 *
 * @author: Alexey.Ivanov
 */
public abstract class JavaFxNamedStub<T extends PsiNamedElement> extends StubBase<T> implements NamedStub<T> {
  private final String myName;

  JavaFxNamedStub(final StubElement parent, final IStubElementType elementType, final String name) {
    super(parent, elementType);
    myName = name;
  }

  public String getName() {
    return myName;
  }
}
