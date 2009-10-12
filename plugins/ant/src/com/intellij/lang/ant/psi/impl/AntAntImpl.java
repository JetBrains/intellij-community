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

import com.intellij.lang.ant.psi.AntAnt;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.reference.AntTargetReference;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class AntAntImpl extends AntTaskImpl implements AntAnt {

  @NonNls private static final String DEFAULT_ANTFILE = "build.xml";

  public AntAntImpl(final AntElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("Ant[");
      builder.append(getFileName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  public List<String> getFileReferenceAttributes() {
    final ArrayList<String> attribs = new ArrayList<String>(super.getFileReferenceAttributes());
    attribs.add("antfile");
    attribs.add("output");
    return attribs;
  }

  @Nullable
  public PsiFile getCalledAntFile() {
    return findFileByName(getFileName(), getDir());
  }

  @NotNull
  public PsiReference[] getReferences() {
    final PsiReference[] result = super.getReferences();
    for (final PsiReference reference : result) {
      if (reference instanceof AntTargetReference) {
        ((AntTargetReference)reference).setShouldBeSkippedByAnnotator(getCalledAntFile() == null);
      }
    }
    return result;
  }

  @NotNull
  private String getFileName() {
    final String result = getSourceElement().getAttributeValue("antfile");
    if (result == null) {
      return DEFAULT_ANTFILE;
    }
    return result;
  }

  @Nullable
  private String getDir() {
    return getSourceElement().getAttributeValue("dir");
  }
}
