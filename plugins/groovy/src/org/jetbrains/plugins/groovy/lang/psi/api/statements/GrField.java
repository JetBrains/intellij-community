package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiField;
import com.intellij.psi.PsiMethod;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author ven
 */
public interface GrField extends GrVariable, GrMember, PsiField, GrTopLevelDefintion {
  public static final GrField[] EMPTY_ARRAY = new GrField[0];

  boolean isProperty();

  @Nullable
  PsiMethod getSetter();

  @Nullable
  PsiMethod getGetter();
}
