package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArguments;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationArgumentsImpl extends GroovyPsiElementImpl implements GrAnnotationArguments {
  public GrAnnotationArgumentsImpl (@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Annotation arguments";
  }
}
