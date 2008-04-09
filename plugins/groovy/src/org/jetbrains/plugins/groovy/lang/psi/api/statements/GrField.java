package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiField;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAccessorMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author ven
 */
public interface GrField extends GrVariable, GrMember, PsiField, GrTopLevelDefintion {
  public static final GrField[] EMPTY_ARRAY = new GrField[0];

  boolean isProperty();

  @Nullable
  GrAccessorMethod getSetter();

  @NotNull
  GrAccessorMethod[] getGetters();
}
