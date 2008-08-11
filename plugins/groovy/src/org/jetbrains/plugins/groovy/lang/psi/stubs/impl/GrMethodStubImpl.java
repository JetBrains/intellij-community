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
  private final String[] myAnnotations;

  public GrMethodStubImpl(StubElement parent, StringRef name, final String[] annotations) {
    super(parent, GroovyElementTypes.METHOD_DEFINITION);
    myName = name;
    myAnnotations = annotations;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }
}
