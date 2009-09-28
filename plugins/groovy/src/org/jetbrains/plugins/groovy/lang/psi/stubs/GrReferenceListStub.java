package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.psi.stubs.StubElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;

/**
 * @author ilyas
 */
public interface GrReferenceListStub extends StubElement<GrReferenceList> {

  String[] getBaseClasses();
}
