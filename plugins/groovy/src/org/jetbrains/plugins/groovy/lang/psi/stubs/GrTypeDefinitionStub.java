package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public interface GrTypeDefinitionStub extends NamedStub<GrTypeDefinition> {

  String[] getSuperClassNames();

}
