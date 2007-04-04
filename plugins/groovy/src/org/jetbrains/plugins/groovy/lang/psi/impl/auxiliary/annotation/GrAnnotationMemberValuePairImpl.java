package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValuePair;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationMemberValuePairImpl extends GroovyPsiElementImpl implements GrAnnotationMemberValuePair {
  public GrAnnotationMemberValuePairImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Annotation member value pair";
  }
}
