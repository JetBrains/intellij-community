package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

/**
 * @author ilyas
 */
public class GrMethodStubImpl extends StubBase<GrMethod> implements GrMethodStub {
  private final StringRef myName;

  public GrMethodStubImpl(StubElement parent, StringRef name) {
    super(parent, GroovyElementTypes.METHOD_DEFINITION);
    myName = name;
  }

  public String getName() {
    return StringRef.toString(myName);
  }
}
