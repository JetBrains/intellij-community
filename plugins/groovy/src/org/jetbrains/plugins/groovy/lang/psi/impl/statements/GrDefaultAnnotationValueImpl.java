package org.jetbrains.plugins.groovy.lang.psi.impl.statements;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrDefaultAnnotationValue;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.06.2007
 */
public class GrDefaultAnnotationValueImpl extends GroovyPsiElementImpl implements GrDefaultAnnotationValue {
  public GrDefaultAnnotationValueImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Default annotation value";
  }
}
