package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef;

import com.intellij.psi.StubBasedPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

/**
 * @author ilyas
 */
public interface GrReferenceList extends GroovyPsiElement, StubBasedPsiElement<GrReferenceListStub> {
  GrCodeReferenceElement[] getReferenceElements();
}
