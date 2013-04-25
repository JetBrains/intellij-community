package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiType;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.GrModifier;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMembersDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 27.03.2007
 */
public interface GrVariableDeclaration extends GrStatement, GrMembersDeclaration {
  ArrayFactory<GrVariableDeclaration> ARRAY_FACTORY = new ArrayFactory<GrVariableDeclaration>() {
    @NotNull
    @Override
    public GrVariableDeclaration[] create(int count) {
      return new GrVariableDeclaration[count];
    }
  };

  @Nullable
  GrTypeElement getTypeElementGroovy();

  @NotNull
  GrVariable[] getVariables();

  void setType(@Nullable PsiType type);

  boolean isTuple();

  @Nullable
  GrTypeElement getTypeElementGroovyForVariable(GrVariable var);

  @Nullable
  GrExpression getTupleInitializer();

  @Override
  boolean hasModifierProperty(@GrModifier.GrModifierConstant @NonNls @NotNull String name);
}
