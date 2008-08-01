package org.jetbrains.plugins.groovy.lang.psi.impl.stubs;

import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.psi.stubs.IStubElementType;

/**
 * @author ilyas
 */
public class GrTypeDefinitionStubImpl extends StubBase<GrTypeDefinition> implements GrTypeDefinitionStub {
  private final String myName;
  private final String[] mySuperClasses;

  public GrTypeDefinitionStubImpl(final  String name, StubElement parent, final String[] supers, final IStubElementType elementType) {
    super(parent, elementType);
    myName = name;
    mySuperClasses = supers;
  }

  public String[] getSuperClassNames() {
    return mySuperClasses;
  }

  public String getName() {
    return myName;
  }
}
