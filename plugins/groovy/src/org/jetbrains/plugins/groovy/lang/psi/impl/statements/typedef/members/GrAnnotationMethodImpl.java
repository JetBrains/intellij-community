package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationMethodStub;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.06.2007
 */
public class GrAnnotationMethodImpl extends GrMethodBaseImpl<GrAnnotationMethodStub>
    implements GrAnnotationMethod, StubBasedPsiElement<GrAnnotationMethodStub> {

  public GrAnnotationMethodImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnnotationMethodImpl(final GrAnnotationMethodStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION_METHOD);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDefaultAnnotationMember(this);
  }

  public String toString() {
    return "Default annotation member";
  }
}
