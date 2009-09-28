package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;

/**
 * @author ilyas
 */
public class GrDirectInheritorsIndex extends StringStubIndexExtension<GrReferenceList> {
  public static final StubIndexKey<String, GrReferenceList> KEY = StubIndexKey.createIndexKey("gr.class.super");

  public StubIndexKey<String, GrReferenceList> getKey() {
    return KEY;
  }

}