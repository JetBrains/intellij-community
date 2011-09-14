/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotationMemberValue;
import com.intellij.psi.PsiAnnotationOwner;
import com.intellij.psi.PsiJavaCodeReferenceElement;
import com.intellij.psi.PsiManager;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.psi.meta.PsiMetaData;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;

/**
 * @author Max Medvedev
 */
public class GrLightAnnotation extends LightElement implements GrAnnotation {
  private final GrEmptyAnnotationArgumentList myAnnotationArgList;

  private final String myQualifiedName;
  private PsiAnnotationOwner myOwner;
  private GrLightClassReferenceElement myRef;

  public GrLightAnnotation(PsiManager manager, Language language, String qualifiedName, PsiAnnotationOwner owner) {
    super(manager, language);
    myQualifiedName = qualifiedName;
    myOwner = owner;

    myAnnotationArgList = new GrEmptyAnnotationArgumentList(manager, language);
    myRef = new GrLightClassReferenceElement(qualifiedName, qualifiedName, this);
  }

  @NotNull
  @Override
  public GrCodeReferenceElement getClassReference() {
    return myRef;
  }

  @NotNull
  @Override
  public String getShortName() {
    return StringUtil.getShortName(myQualifiedName);
  }

  @NotNull
  @Override
  public GrAnnotationArgumentList getParameterList() {
    return myAnnotationArgList;
  }

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  @Override
  public void acceptChildren(GroovyElementVisitor visitor) {
    //todo
  }

  @Override
  public String toString() {
    return "light groovy annotation";
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return null;  
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(@NonNls String attributeName) {
    return null;
  }

  @Override
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls String attributeName) {
    return null;
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    throw new UnsupportedOperationException("light annotation does not support changes");
  }

  @Override
  public PsiAnnotationOwner getOwner() {
    return myOwner;
  }

  @Override
  public PsiMetaData getMetaData() {
    return null;
  }

  private static class GrEmptyAnnotationArgumentList extends LightElement implements GrAnnotationArgumentList {
    private GrEmptyAnnotationArgumentList(PsiManager manager, Language language) {
      super(manager, language);
    }

    @NotNull
    @Override
    public GrAnnotationNameValuePair[] getAttributes() {
      return GrAnnotationNameValuePair.EMPTY_ARRAY;
    }

    @Override
    public void accept(GroovyElementVisitor visitor) {
      visitor.visitAnnotationArgumentList(this);
    }

    @Override
    public void acceptChildren(GroovyElementVisitor visitor) {
      //todo
    }

    @Override
    public String toString() {
      return "light empty annotation argument list";
    }
  }
}
