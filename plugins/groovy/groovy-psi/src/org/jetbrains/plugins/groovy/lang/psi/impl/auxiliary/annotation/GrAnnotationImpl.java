// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.auxiliary.annotation;

import com.intellij.codeInsight.AnnotationTargetUtil;
import com.intellij.lang.ASTNode;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.source.tree.java.PsiAnnotationImpl;
import com.intellij.util.PairFunction;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.parser.GroovyEmptyStubElementTypes;
import org.jetbrains.plugins.groovy.lang.parser.GroovyStubElementTypes;
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

import static org.jetbrains.plugins.groovy.lang.resolve.imports.GroovyImports.getAliasedFullyQualifiedNames;

/**
 * @author Dmitry.Krasilschikov
 */
public class GrAnnotationImpl extends GrStubElementBase<GrAnnotationStub> implements GrAnnotation, StubBasedPsiElement<GrAnnotationStub> {

  private static final PairFunction<Project, String, PsiAnnotation> ANNOTATION_CREATOR =
    (project, text) -> GroovyPsiElementFactory.getInstance(project).createAnnotationFromText(text);

  public GrAnnotationImpl(@NotNull ASTNode node) {
    super(node);
  }

  public GrAnnotationImpl(GrAnnotationStub stub) {
    super(stub, GroovyStubElementTypes.ANNOTATION);
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  @Override
  public String toString() {
    return "Annotation";
  }

  @Override
  public @NotNull GrAnnotationArgumentList getParameterList() {
    return getRequiredStubOrPsiChild(GroovyEmptyStubElementTypes.ANNOTATION_ARGUMENT_LIST);
  }

  @Override
  public @Nullable @NonNls String getQualifiedName() {
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
  public @Nullable PsiJavaCodeReferenceElement getNameReferenceElement() {
    final GroovyResolveResult resolveResult = getClassReference().advancedResolve();

    final PsiElement resolved = resolveResult.getElement();
    if (!(resolved instanceof PsiClass)) return null;

    return new LightClassReference(getManager(), getClassReference().getText(), (PsiClass)resolved, resolveResult.getSubstitutor());
  }

  @Override
  public @Nullable PsiAnnotationMemberValue findAttributeValue(@Nullable String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  public @Nullable PsiAnnotationMemberValue findDeclaredAttributeValue(final @NonNls String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@Nullable @NonNls String attributeName, T value) {
    //noinspection unchecked
    return (T)PsiImplUtil.setDeclaredAttributeValue(this, attributeName, value, ANNOTATION_CREATOR);
  }

  @Override
  public @NotNull GrCodeReferenceElement getClassReference() {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      return stub.getPsiElement().getClassReference();
    }

    return findNotNullChildByClass(GrCodeReferenceElement.class);
  }

  @Override
  public @NotNull String getShortName() {
    final GrAnnotationStub stub = getStub();
    if (stub != null) {
      return PsiAnnotationImpl.getAnnotationShortName(stub.getText());
    }

    final String referenceName = getClassReference().getReferenceName();
    assert referenceName != null;
    return referenceName;
  }

  @Override
  public @Nullable PsiAnnotationOwner getOwner() {
    PsiElement parent = getParent();
    return parent instanceof PsiAnnotationOwner ? (PsiAnnotationOwner)parent : null;
  }

  @Override
  public boolean hasQualifiedName(@NotNull String qualifiedName) {
    return mayHaveQualifiedName(qualifiedName) && qualifiedName.equals(getQualifiedName());
  }

  private boolean mayHaveQualifiedName(@NotNull String qualifiedName) {
    String shortName = getShortName();
    return shortName.equals(StringUtil.getShortName(qualifiedName)) ||
           getAliasedFullyQualifiedNames(this, shortName).contains(qualifiedName);
  }

  public static TargetType @NotNull [] getApplicableElementTypeFields(PsiElement owner) {
    if (owner instanceof PsiClass aClass) {
      if (aClass.isAnnotationType()) {
        return addTypeUseIfApplicable(owner, TargetType.ANNOTATION_TYPE, TargetType.TYPE);
      }
      else if (aClass instanceof GrTypeParameter) {
        return addTypeUseIfApplicable(owner, TargetType.TYPE_PARAMETER);
      }
      else {
        return addTypeUseIfApplicable(owner, TargetType.TYPE);
      }
    }
    if (owner instanceof GrMethod) {
      if (((PsiMethod)owner).isConstructor()) {
        return addTypeUseIfApplicable(owner, TargetType.CONSTRUCTOR);
      }
      else {
        return addTypeUseIfApplicable(owner, TargetType.METHOD);
      }
    }
    if (owner instanceof GrVariableDeclaration) {
      final GrVariable[] variables = ((GrVariableDeclaration)owner).getVariables();
      if (variables.length == 0) {
        return TargetType.EMPTY_ARRAY;
      }
      if (variables[0] instanceof GrField || ResolveUtil.isScriptField(variables[0])) {
        return addTypeUseIfApplicable(owner, TargetType.FIELD);
      }
      else {
        return addTypeUseIfApplicable(owner, TargetType.LOCAL_VARIABLE);
      }
    }
    if (owner instanceof GrParameter) {
      return addTypeUseIfApplicable(owner, TargetType.PARAMETER);
    }
    if (owner instanceof GrPackageDefinition) {
      return new TargetType[]{TargetType.PACKAGE};
    }
    if (owner instanceof GrTypeElement) {
      return new TargetType[]{TargetType.TYPE_USE};
    }
    if (owner instanceof GrCodeReferenceElement) {
      return new TargetType[]{TargetType.TYPE_USE};
    }


    return TargetType.EMPTY_ARRAY;
  }

  private static TargetType[] addTypeUseIfApplicable(@NotNull PsiElement element, TargetType @NotNull ... baseTargetTypeArray) {
    if (GroovyConfigUtils.isAtLeastGroovy40(element)) {
      int length = baseTargetTypeArray.length;
      TargetType[] newTargetTypeArray = new TargetType[length + 1];
      System.arraycopy(baseTargetTypeArray, 0, newTargetTypeArray, 0, length);
      newTargetTypeArray[length] = TargetType.TYPE_USE;
      return newTargetTypeArray;
    } else {
      return baseTargetTypeArray;
    }
  }

  public static boolean isAnnotationApplicableTo(GrAnnotation annotation, TargetType @NotNull ... elementTypeFields) {
    return elementTypeFields.length == 0 || AnnotationTargetUtil.findAnnotationTarget(annotation, elementTypeFields) != null;
  }
}
