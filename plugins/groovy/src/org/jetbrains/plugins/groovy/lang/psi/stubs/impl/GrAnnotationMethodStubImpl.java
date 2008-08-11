package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationMethodStub;

/**
 * @author ilyas
 */
public class GrAnnotationMethodStubImpl extends StubBase<GrAnnotationMethod> implements GrAnnotationMethodStub {
  private final StringRef myName;

  public GrAnnotationMethodStubImpl(StubElement parent, StringRef name) {
    super(parent, GroovyElementTypes.ANNOTATION_METHOD);
    myName = name;
  }

  public String getName() {
    return StringRef.toString(myName);
  }
}
