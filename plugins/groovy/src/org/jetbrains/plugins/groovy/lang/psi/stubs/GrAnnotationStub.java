package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.util.io.StringRef;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

/**
 * @author peter
 */
public class GrAnnotationStub extends StubBase<GrAnnotation> {
  private final StringRef myReference;

  public GrAnnotationStub(StubElement parent, StringRef reference) {
    super(parent, GroovyElementTypes.ANNOTATION);
    myReference = reference;
  }

  public GrAnnotationStub(StubElement parent, GrAnnotation from) {
    super(parent, GroovyElementTypes.ANNOTATION);
    myReference = StringRef.fromString(from.getClassReference().getReferenceName());
  }

  public String getAnnotationName() {
    return myReference.getString();
  }
}
