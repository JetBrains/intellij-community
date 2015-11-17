/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.meta.PsiMetaData;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.parser.GroovyElementTypes;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
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
  private static final Logger LOG = Logger.getInstance("#org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation.GrAnnotationImpl");

  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR = new PairFunction<Project, String, PsiAnnotation>() {
    @Override
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

  @Override
  public void accept(GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  public String toString() {
    return "Annotation";
  }

  @Override
  @NotNull
  public GrAnnotationArgumentList getParameterList() {
    return findNotNullChildByClass(GrAnnotationArgumentList.class);
  }

  @Override
  @Nullable
  @NonNls
  public String getQualifiedName() {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getQualifiedName();
    }

    final GrCodeReferenceElement nameRef = getClassReference();
    final PsiElement resolved = nameRef.resolve();
    if (resolved instanceof PsiClass) return ((PsiClass)resolved).getQualifiedName();
    return null;
  }

  @Override
  @Nullable
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    final GroovyResolveResult resolveResult = resolveWithStub();

    final PsiElement resolved = resolveResult.getElement();
    if (!(resolved instanceof PsiClass)) return null;

    return new LightClassReference(getManager(), getClassReference().getText(), (PsiClass)resolved, resolveResult.getSubstitutor());
  }

  @NotNull
  private GroovyResolveResult resolveWithStub() {
    final GrAnnotationStub stub = getStub();
    final GrCodeReferenceElement reference = stub != null ? stub.getPsiElement().getClassReference() : getClassReference();
    return reference.advancedResolve();
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findAttributeValue(@Nullable String attributeName) {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      final GrAnnotation stubbedPsi = stub.getPsiElement();
      final PsiAnnotationMemberValue value = PsiImplUtil.findAttributeValue(stubbedPsi, attributeName);
      if (value == null || !PsiTreeUtil.isAncestor(stubbedPsi, value, true)) {         // if value is a default value we can use it
        return value;
      }
    }
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  @Nullable
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls final String attributeName) {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      final GrAnnotation stubbedPsi = stub.getPsiElement();
      final PsiAnnotationMemberValue value = PsiImplUtil.findDeclaredAttributeValue(stubbedPsi, attributeName);
      if (value == null) {
        return null;
      }
    }
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable @NonNls String attributeName, T value) {
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  @Override
  @Nullable
  public PsiMetaData getMetaData() {
    return null;
  }

  @Override
  @NotNull
  public GrCodeReferenceElement getClassReference() {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getClassReference();
    }

    return findNotNullChildByClass(GrCodeReferenceElement.class);
  }

  @Override
  @NotNull
  public String getShortName() {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getShortName();
    }

    final String referenceName = getClassReference().getReferenceName();
    assert referenceName != null;
    return referenceName;
  }

  @Override
  @Nullable
  public PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();
    return parent instanceof PsiAnnotationOwner ? (PsiAnnotationOwner)parent : null;
  }

  @NotNull
  public static TargetType[] getApplicableElementTypeFields(PsiElement owner) {
    if (owner instanceof PsiClass) {
      PsiClass aClass = (PsiClass)owner;
      if (aClass.isAnnotationType()) {
        return new TargetType[]{TargetType.ANNOTATION_TYPE, TargetType.TYPE};
      }
      else if (aClass instanceof GrTypeParameter) {
        return new TargetType[]{TargetType.TYPE_PARAMETER};
      }
      else {
        return new TargetType[]{TargetType.TYPE};
      }
    }
    if (owner instanceof GrMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return new TargetType[]{TargetType.CONSTRUCTOR};
      }
      else {
        return new TargetType[]{TargetType.METHOD};
      }
    }
    if (owner instanceof GrVariableDeclaration) {
      final GrVariable[] variables = ((GrVariableDeclaration)owner).getVariables();
      if (variables.length == 0) {
        return TargetType.EMPTY_ARRAY;
      }
      if (variables[0] instanceof GrField || ResolveUtil.isScriptField(variables[0])) {
        return new TargetType[]{TargetType.FIELD};
      }
      else {
        return new TargetType[]{TargetType.LOCAL_VARIABLE};
      }
    }
    if (owner instanceof GrParameter) {
      return new TargetType[]{TargetType.PARAMETER};
    }
    if (owner instanceof GrPackageDefinition) {
      return new TargetType[]{TargetType.PACKAGE};
    }
    if (owner instanceof GrTypeElement) {
      return new TargetType[]{TargetType.TYPE_USE};
    }

    return TargetType.EMPTY_ARRAY;
  }

  public static boolean isAnnotationApplicableTo(GrAnnotation annotation, @NotNull TargetType... elementTypeFields) {
    return elementTypeFields.length == 0 || AnnotationTargetUtil.findAnnotationTarget(annotation, elementTypeFields) != null;
  }
}
