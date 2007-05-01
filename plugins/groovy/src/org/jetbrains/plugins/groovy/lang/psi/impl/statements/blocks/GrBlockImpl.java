package org.jetbrains.plugins.groovy.lang.psi.impl.statements.blocks;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrCodeBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrStatement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;
import org.jetbrains.annotations.NotNull;
import com.intellij.lang.ASTNode;
import com.intellij.psi.scope.PsiScopeProcessor;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiElement;

/**
 * @author ven
 */
public abstract class GrBlockImpl extends GroovyPsiElementImpl implements GrCodeBlock {
  public GrBlockImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrStatement[] getStatements() {
    return findChildrenByClass(GrStatement.class);
  }

  public boolean processDeclarations(@NotNull PsiScopeProcessor processor, @NotNull PsiSubstitutor substitutor, PsiElement lastParent, @NotNull PsiElement place) {
    return ResolveUtil.processChildren(this, processor, substitutor, lastParent, place);
  }
}
