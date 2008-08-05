package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrDefaultAnnotationMethod;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.06.2007
 */
public class GrDefaultAnnotationMethodImpl extends GrMethodImpl implements GrDefaultAnnotationMethod {
  public GrDefaultAnnotationMethodImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitDefaultAnnotationMember(this);
  }

  public String toString() {
    return "Default annotation member";
  }
}
