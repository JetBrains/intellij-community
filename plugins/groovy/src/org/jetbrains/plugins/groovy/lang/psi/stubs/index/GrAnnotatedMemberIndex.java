package org.jetbrains.plugins.groovy.lang.psi.stubs.index;

import com.intellij.psi.PsiMember;
import com.intellij.psi.stubs.StringStubIndexExtension;
import com.intellij.psi.stubs.StubIndexKey;

/**
 * @author ilyas
 */
public class GrAnnotatedMemberIndex extends StringStubIndexExtension<PsiMember> {
  public static final StubIndexKey<String, PsiMember> KEY = StubIndexKey.createIndexKey("gr.annot.members");

  public StubIndexKey<String, PsiMember> getKey() {
    return KEY;
  }
}