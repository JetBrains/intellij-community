/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.stubs;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.stubs.StubBase;
import com.intellij.psi.stubs.StubElement;
import com.intellij.reference.SoftReference;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

/**
 * @author peter
 */
public class GrAnnotationStub extends StubBase<GrAnnotation> {
  private static final Logger LOG = Logger.getInstance(GrAnnotationStub.class);

  private final String myText;
  private SoftReference<GrAnnotation> myPsiRef;

  public GrAnnotationStub(StubElement parent, String text) {
    super(parent, GroovyElementTypes.ANNOTATION);
    myText = text;
  }

  public GrAnnotationStub(StubElement parent, GrAnnotation from) {
    super(parent, GroovyElementTypes.ANNOTATION);
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
