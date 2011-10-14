
package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiElement;
import org.jetbrains.annotations.Nullable;
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

  @Nullable
  PsiElement getLBrace();

  @Nullable
  PsiElement getRParenth();
}