package org.jetbrains.plugins.groovy.lang.psi.impl.statements.typedef.members;

import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrDefaultAnnotationMember;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import com.intellij.psi.PsiElement;
import com.intellij.util.IncorrectOperationException;
import com.intellij.lang.ASTNode;

/**
 * User: Dmitry.Krasilschikov
 * Date: 04.06.2007
 */
public class GrDefaultAnnotationMemberImpl extends GroovyPsiElementImpl implements GrDefaultAnnotationMember {
  public GrDefaultAnnotationMemberImpl(@NotNull ASTNode node) {
    super(node);
  }

  //TODO: NIY
  public PsiElement setName(@NonNls @NotNull String name) throws IncorrectOperationException {
    return null;
  }

  public String toString() {
    return "Default annotation member";
  }
}
