package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public interface GrTypeDefinitionStub extends NamedStub<GrTypeDefinition> {

  String[] getSuperClassNames();

  @NonNls
  @Nullable
  String getQualifiedName();

  String getSourceFileName();

  String[] getAnnotations();

  boolean isAnonymous();
  boolean isInterface();
  boolean isEnum();
  boolean isAnnotationType();

  byte getFlags();
}
