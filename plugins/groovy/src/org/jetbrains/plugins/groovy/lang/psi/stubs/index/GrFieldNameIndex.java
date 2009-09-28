package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;

/**
 * @author ilyas
 */
public class GrFieldNameIndex extends StringStubIndexExtension<GrField> {
  public static final StubIndexKey<String, GrField> KEY = StubIndexKey.createIndexKey("gr.field.name");

  public StubIndexKey<String, GrField> getKey() {
    return KEY;
  }
}
