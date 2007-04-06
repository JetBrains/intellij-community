package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.enumConstant;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrEnumConstants;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public class GrEnumConstantsImpl extends GroovyPsiElementImpl implements GrEnumConstants {
  public GrEnumConstantsImpl (@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Enumeration constants";
  }
}
