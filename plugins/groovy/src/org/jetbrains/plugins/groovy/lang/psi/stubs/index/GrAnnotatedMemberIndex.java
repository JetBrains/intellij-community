package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;

/**
 * @author ilyas
 */
public class GrAnnotatedMemberIndex extends StringStubIndexExtension<GrMember> {
  public static final StubIndexKey<String, GrMember> KEY = StubIndexKey.createIndexKey("gr.annot.members");

  public StubIndexKey<String, GrMember> getKey() {
    return KEY;
  }
}