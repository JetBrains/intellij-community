
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;

/**
 * @author ilyas
 */
public interface GrFinallyClause extends GroovyPsiElement {

  public GrOpenBlock getBody();

}