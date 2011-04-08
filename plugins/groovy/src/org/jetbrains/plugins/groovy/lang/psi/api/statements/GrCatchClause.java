
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrOpenBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;

/**
 * @author ilyas
 */
public interface GrCatchClause extends GrParametersOwner {

  @Nullable
  GrParameter getParameter();

  @Nullable
  GrOpenBlock getBody();

}