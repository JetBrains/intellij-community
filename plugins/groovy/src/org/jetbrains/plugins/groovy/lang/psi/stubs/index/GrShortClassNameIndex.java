package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

/**
 * @author ilyas
 */
public class GrShortClassNameIndex extends StringStubIndexExtension<GrTypeDefinition> {
  public static final StubIndexKey<String, GrTypeDefinition> KEY = StubIndexKey.createIndexKey("gr.class.shortName");

  public StubIndexKey<String, GrTypeDefinition> getKey() {
    return KEY;
  }
}
