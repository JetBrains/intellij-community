package org.jetbrains.plugins.groovy.lang.psi.stubs.impl;

import com.intellij.psi.stubs.IStubElementType;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrReferenceList;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrReferenceListStub;

/**
 * @author ilyas
 */
public class GrReferenceListStubImpl extends StubBase<GrReferenceList> implements GrReferenceListStub {

  private final String[] myRefNames;

  public GrReferenceListStubImpl(final StubElement parentStub, IStubElementType elemtType, final String[] refNames) {
    super(parentStub, elemtType);
    myRefNames = refNames;
  }

  public String[] getBaseClasses() {
    return myRefNames;
  }
}
