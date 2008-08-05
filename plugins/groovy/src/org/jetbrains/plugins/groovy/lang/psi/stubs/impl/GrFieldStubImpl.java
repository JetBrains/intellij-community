package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrFieldStub;

/**
 * @author ilyas
 */
public class GrFieldStubImpl extends StubBase<GrField> implements GrFieldStub {

  private final boolean isEnumConstant;
  private final StringRef myName;

  public GrFieldStubImpl(StubElement parent,
                         String name,
                         boolean isEnumConstant) {
    super(parent, GroovyElementTypes.FIELD);
    myName = StringRef.fromString(name);
    this.isEnumConstant = isEnumConstant;

  }

  public boolean isEnumConstant() {
    return isEnumConstant;
  }

  public String getName() {
    return StringRef.toString(myName);
  }
}
