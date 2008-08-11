package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrAnnotationMethod;

/**
 * @author ilyas
 */
public class GrAnnotationMethodNameIndex extends StringStubIndexExtension<GrAnnotationMethod> {
  public static final StubIndexKey<String, GrAnnotationMethod> KEY = StubIndexKey.createIndexKey("gr.annot.method.name");

  public StubIndexKey<String, GrAnnotationMethod> getKey() {
    return KEY;
  }
}
