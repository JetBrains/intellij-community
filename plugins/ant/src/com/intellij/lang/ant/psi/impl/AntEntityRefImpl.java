/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.misc.PsiReferenceListSpinAllocator;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.impl.reference.AntEntityReference;
import com.intellij.psi.PsiReference;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlElement;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class AntEntityRefImpl extends AntElementImpl {

  private PsiReference[] myRefs;

  public AntEntityRefImpl(final AntStructuredElement parent, final XmlElement sourceElement) {
    super(parent, sourceElement);
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      myRefs = null;
    }
  }

  @NotNull
  public PsiReference[] getReferences() {
    synchronized (PsiLock.LOCK) {
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
}
