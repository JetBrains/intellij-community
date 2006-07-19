package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.reference.AntEntityReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntEntityRefImpl extends AntElementImpl {

  private PsiReference[] myRefs;

  public AntEntityRefImpl(final AntElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public void clearCaches() {
    super.clearCaches();
    myRefs = null;
  }

  @NotNull
  public PsiReference[] getReferences() {
    if (myRefs == null) {
      final List<PsiReference> refList = PsiReferenceListSpinAllocator.alloc();
      try {
        for (final PsiReference ref : getSourceElement().getReferences()) {
          refList.add(new AntEntityReference(this, ref));
        }
        myRefs = refList.toArray(new PsiReference[refList.size()]);
      }
      finally {
        PsiReferenceListSpinAllocator.dispose(refList);
      }
    }
    return myRefs;
  }
}
