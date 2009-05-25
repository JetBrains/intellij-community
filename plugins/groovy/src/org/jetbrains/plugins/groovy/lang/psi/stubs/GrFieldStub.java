package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

import java.util.Set;

/**
 * @author ilyas
 */
public interface GrFieldStub extends NamedStub<GrField> {

  //todo add type info
  //todo add initializer info

  String[] getAnnotations();

  boolean isEnumConstant();

  @Nullable
  Set<String>[] getNamedParameters();

}
