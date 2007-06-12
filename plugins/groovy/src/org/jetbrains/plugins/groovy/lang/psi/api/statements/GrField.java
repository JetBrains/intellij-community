package org.jetbrains.plugins.groovy.lang.psi.api.statements;

import com.intellij.psi.PsiField;

/**
 * @author ven
 */
public interface GrField extends GrVariable, PsiField, GrTopLevelDefintion {
  public static final GrField[] EMPTY_ARRAY = new GrField[0];

  boolean isProperty();
}
