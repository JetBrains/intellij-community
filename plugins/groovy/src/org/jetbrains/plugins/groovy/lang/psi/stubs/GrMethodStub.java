package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.NamedStub;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

import java.util.Set;

/**
 * @author ilyas
 */
public interface GrMethodStub extends NamedStub<GrMethod> {

  String[] getAnnotations();

  @NotNull
  Set<String>[] getNamedParameters();
}
