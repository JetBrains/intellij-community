package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrTypeDefinitionStub;

/**
 * @author ilyas
 */
public class GrTypeDefinitionStubImpl extends StubBase<GrTypeDefinition> implements GrTypeDefinitionStub {
  private final StringRef myName;
  private final String[] mySuperClasses;
  private final StringRef myQualifiedName;
  private StringRef mySourceFileName;

  public GrTypeDefinitionStubImpl(StubElement parent, 
                                  final String name,
                                  final String[] supers,
                                  final IStubElementType elementType,
                                  final String qualifiedName) {
    super(parent, elementType);
    myName = StringRef.fromString(name);
    mySuperClasses = supers;
    myQualifiedName = StringRef.fromString(qualifiedName);
  }

  public String[] getSuperClassNames() {
    return mySuperClasses;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  public String getQualifiedName() {
    return StringRef.toString(myQualifiedName);
  }

  public String getSourceFileName() {
    return StringRef.toString(mySourceFileName);
  }

  public void setSourceFileName(final StringRef sourceFileName) {
    mySourceFileName = sourceFileName;
  }

  public void setSourceFileName(final String sourceFileName) {
    mySourceFileName = StringRef.fromString(sourceFileName);
  }

}
