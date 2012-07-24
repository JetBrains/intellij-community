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

package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrVariableDeclaration;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.params.GrParameter;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.api.toplevel.packaging.GrPackageDefinition;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrStubElementBase;
import org.jetbrains.plugins.groovy.lang.psi.stubs.GrAnnotationStub;
import org.jetbrains.plugins.groovy.lang.resolve.ResolveUtil;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 04.04.2007
 */
public class GrAnnotationImpl extends GrStubElementBase<GrAnnotationStub> implements GrAnnotation, StubBasedPsiElement<GrAnnotationStub> {
  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR = new PairFunction<Project, String, PsiAnnotation>() {
    public PsiAnnotation fun(Project project, String text) {
      return GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(text);
    }
  };

  public GrAnnotationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnnotationImpl(GrAnnotationStub stub) {
    super(stub, GroovyElementTypes.ANNOTATION);
  }

  @Override
  public PsiElement getParent() {
    return getParentByStub();
  }

  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  public String toString() {
    return "Annotation";
  }

  @NotNull
  public GrAnnotationArgumentList getParameterList() {
    return findNotNullChildByClass(GrAnnotationArgumentList.class);
  }

  @Nullable
  @NonNls
  public String getQualifiedName() {
    final GrCodeReferenceElement nameRef = getClassReference();
    final PsiElement resolved = nameRef.resolve();
    if (resolved instanceof PsiClass) return ((PsiClass) resolved).getQualifiedName();
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

  public <T extends PsiAnnotationMemberValue>  T setDeclaredAttributeValue(@Nullable @NonNls String attributeName, T value) {
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  @Nullable
  public PsiMetaData getMetaData() {
    return null;
  }

  @NotNull
  public GrCodeReferenceElement getClassReference() {
    return findNotNullChildByClass(GrCodeReferenceElement.class);
  }

  @NotNull
  public String getShortName(){
    //Annotation is an identifier always
    return getClassReference().getText();
  }

  public PsiAnnotationOwner getOwner() {
    return (PsiAnnotationOwner)getParent();
  }

  @Nullable
  public static String[] getApplicableElementTypeFields(PsiElement owner) {
    if (owner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)owner;
      if (aClass.isAnnotationType()) {
        return new String[]{"ANNOTATION_TYPE", "TYPE"};
      }
      else if (aClass instanceof GrTypeParameter) {
        return new String[]{"TYPE_PARAMETER"};
      }
      else {
        return new String[]{"TYPE"};
      }
    }
    if (owner instanceof GrMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return new String[]{"CONSTRUCTOR"};
      }
      else {
        return new String[]{"METHOD"};
      }
    }
    if (owner instanceof GrVariableDeclaration) {
      final GrVariable[] variables = ((GrVariableDeclaration)owner).getVariables();
      if (variables.length == 0) return null;
      if (variables[0] instanceof GrField || ResolveUtil.isScriptField(variables[0])) {
        return new String[]{"FIELD"};
      }
      else {
        return new String[]{"LOCAL_VARIABLE"};
      }
    }
    if (owner instanceof GrParameter) {
      return new String[]{"PARAMETER"};
    }
    if (owner instanceof GrPackageDefinition) {
      return new String[]{"PACKAGE"};
    }
    if (owner instanceof GrTypeElement) {
      return new String[]{"TYPE_USE"};
    }

    return null;
  }


  public static boolean isAnnotationApplicableTo(GrAnnotation annotation, boolean strict, String... elementTypeFields) {
    if (elementTypeFields == null) return true;
    GrCodeReferenceElement nameRef = annotation.getClassReference();
    PsiElement resolved = nameRef.resolve();
    if (resolved instanceof PsiClass && ((PsiClass)resolved).isAnnotationType()) {
      return PsiAnnotationImpl.isAnnotationApplicable(strict, (PsiClass)resolved, elementTypeFields, annotation.getResolveScope());
    }
    return !strict;
  }
}
