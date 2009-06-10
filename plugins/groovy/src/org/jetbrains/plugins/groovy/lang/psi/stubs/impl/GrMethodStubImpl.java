package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrMethodStub;

import java.util.Set;

/**
 * @author ilyas
 */
public class GrMethodStubImpl extends StubBase<GrMethod> implements GrMethodStub {
  private final StringRef myName;
  private final String[] myAnnotations;
  private final Set<String>[] myNamedParameters;

  public GrMethodStubImpl(StubElement parent, StringRef name, final String[] annotations, final @NotNull Set<String>[] namedParameters) {
    super(parent, GroovyElementTypes.METHOD_DEFINITION);
    myName = name;
    myAnnotations = annotations;
    myNamedParameters = namedParameters;
  }

  public String getName() {
    return StringRef.toString(myName);
  }

  public String[] getAnnotations() {
    return myAnnotations;
  }

  @NotNull
  public Set<String>[] getNamedParameters() {
    return myNamedParameters;
  }
}
