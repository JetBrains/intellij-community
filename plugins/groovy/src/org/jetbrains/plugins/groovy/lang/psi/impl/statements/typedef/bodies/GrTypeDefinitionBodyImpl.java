package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.bodies;

import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinitionBody;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.05.2007
 */
public class GrTypeDefinitionBodyImpl extends GroovyPsiElementImpl implements GrTypeDefinitionBody {
  public GrTypeDefinitionBodyImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type definition body";
  }

  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }
}
