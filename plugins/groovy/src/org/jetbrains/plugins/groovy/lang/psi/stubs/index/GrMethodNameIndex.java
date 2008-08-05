package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;

/**
 * @author ilyas
 */
public class GrMethodNameIndex extends StringStubIndexExtension<GrMethod> {
  public static final StubIndexKey<String, GrMethod> KEY = StubIndexKey.createIndexKey("gr.method.name");

  public StubIndexKey<String, GrMethod> getKey() {
    return KEY;
  }
}