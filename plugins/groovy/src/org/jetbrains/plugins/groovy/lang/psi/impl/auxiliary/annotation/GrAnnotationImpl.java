/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.openapi.project.Project;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationImpl extends GroovyPsiElementImpl implements GrAnnotation {
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR = new PairFunction<Project, String, PsiAnnotation>() {
    public PsiAnnotation fun(Project project, String text) {
      return GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(text);
    }
  };

  public GrAnnotationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  public String toString() {
    return "Annotation";
  }

  @NotNull
  public PsiAnnotationParameterList getParameterList() {
    return findNotNullChildByClass(PsiAnnotationParameterList.class);
  }

  @Nullable
  @NonNls
  public String getQualifiedName() {
    final GrCodeReferenceElement nameRef = getClassReference();
    if (nameRef != null) {
      final PsiElement resolved = nameRef.resolve();
      if (resolved instanceof PsiClass) return ((PsiClass) resolved).getQualifiedName();
    }
    return null;
  }

  @Nullable
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    return null;
  }

  public PsiAnnotationMemberValue findAttributeValue(String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@NonNls String attributeName, T value) {
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return null;
  }

  public GrCodeReferenceElement getClassReference() {
    return findChildByClass(GrCodeReferenceElement.class);
  }

  @NotNull
  public String getShortName(){
    //Annotation is an identifier always
    return getClassReference().getText();
  }

  public PsiAnnotationOwner getOwner() {
    return (PsiAnnotationOwner)getParent();
  }

}
