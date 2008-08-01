package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public interface GrTypeDefinitionStub extends NamedStub<GrTypeDefinition> {

  String[] getSuperClassNames();

  @NonNls
  @Nullable
  String getQualifiedName();

  String getSourceFileName();
}
