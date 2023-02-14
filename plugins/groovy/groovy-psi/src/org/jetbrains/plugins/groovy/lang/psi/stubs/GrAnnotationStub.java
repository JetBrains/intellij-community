// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

public class GrAnnotationStub extends StubBase<GrAnnotation> {
  private static final Logger LOG = Logger.getInstance(GrAnnotationStub.class);

  private final String myText;
  private volatile SoftReference<GrAnnotation> myPsiRef;

  public GrAnnotationStub(StubElement parent, String text) {
    super(parent, GroovyStubElementTypes.ANNOTATION);
    myText = text;
  }

  public GrAnnotationStub(StubElement parent, GrAnnotation from) {
    super(parent, GroovyStubElementTypes.ANNOTATION);
    myText = from.getText();
  }

  public GrAnnotation getPsiElement() {
    GrAnnotation annotation = SoftReference.dereference(myPsiRef);
    if (annotation != null) {
      return annotation;
    }
    try {
      annotation = GroovyPsiElementFactory.getInstance(getProject()).createAnnotationFromText(myText, getPsi());
      myPsiRef = new SoftReference<>(annotation);
      return annotation;
    }
    catch (IncorrectOperationException e) {
      LOG.error("Bad annotation in repository!", e);
      return null;
    }
  }

  public String getText() {
    return myText;
  }
}
