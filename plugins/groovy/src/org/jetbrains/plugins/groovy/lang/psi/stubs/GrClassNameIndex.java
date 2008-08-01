package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrClassNameIndex extends StringStubIndexExtension<GrTypeDefinition> {
  public static final StubIndexKey<String, GrTypeDefinition> KEY = StubIndexKey.createIndexKey("Gr.class.shortName");

  public StubIndexKey<String, GrTypeDefinition> getKey() {
    return KEY;
  }
}
