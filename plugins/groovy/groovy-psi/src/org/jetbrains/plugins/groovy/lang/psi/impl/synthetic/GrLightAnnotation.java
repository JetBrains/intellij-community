// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.lang.Language;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiImplUtil;
import com.intellij.psi.impl.light.LightClassReference;
import com.intellij.psi.impl.light.LightElement;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.GroovyPsiElementFactory;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationMemberValue;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotationNameValuePair;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.impl.AnnotationArgConverter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Max Medvedev
 */
public class GrLightAnnotation extends LightElement implements GrAnnotation {
  private final GrLightAnnotationArgumentList myAnnotationArgList;

  private final String myQualifiedName;
  private final PsiAnnotationOwner myOwner;
  private final PsiElement myContext;
  private final GrLightClassReferenceElement myRef;

  public GrLightAnnotation(@NotNull PsiManager manager,
                           @NotNull Language language,
                           @NotNull String qualifiedName,
                           @NotNull PsiModifierList owner) {
    super(manager, language);
    myQualifiedName = qualifiedName;
    myOwner = owner;
    myContext = owner;

    myAnnotationArgList = new GrLightAnnotationArgumentList(manager, language);
    myRef = new GrLightClassReferenceElement(qualifiedName, qualifiedName, this);
  }

  public GrLightAnnotation(@NotNull PsiAnnotationOwner owner,
                           @NotNull PsiElement context,
                           @NotNull String qualifiedName,
                           @NotNull Map<String, String> params ) {
    super(context.getManager(), context.getLanguage());
    myQualifiedName = qualifiedName;
    myOwner = owner;
    myContext = context;

    myAnnotationArgList = new GrLightAnnotationArgumentList(context.getManager(), context.getLanguage());
    myRef = new GrLightClassReferenceElement(qualifiedName, qualifiedName, this);
    params.forEach((key, value) -> addAttribute(key, value));
  }

  @Override
  public PsiElement getContext() {
    return myContext;
  }

  @Override
  public PsiFile getContainingFile() {
    return myContext.getContainingFile();
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
  public void accept(@NotNull GroovyElementVisitor visitor) {
    visitor.visitAnnotation(this);
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
    //todo
  }

  @Override
  public String toString() {
    return "light groovy annotation";
  }

  @Override
  public String getText() {
    return "@" + myQualifiedName + myAnnotationArgList.getText();
  }

  @Override
  public String getQualifiedName() {
    return myQualifiedName;
  }

  @Override
  public PsiJavaCodeReferenceElement getNameReferenceElement() {
    final GroovyResolveResult resolveResult = myRef.advancedResolve();
    final PsiElement resolved = resolveResult.getElement();

    if (resolved instanceof PsiClass) {
      return new LightClassReference(getManager(), getClassReference().getText(), (PsiClass)resolved, resolveResult.getSubstitutor());
    }
    else {
      return null;
    }
  }

  @Override
  public PsiAnnotationMemberValue findAttributeValue(@NonNls String attributeName) {
    return PsiImplUtil.findAttributeValue(this, attributeName);
  }

  @Override
  public PsiAnnotationMemberValue findDeclaredAttributeValue(@NonNls String attributeName) {
    return PsiImplUtil.findDeclaredAttributeValue(this, attributeName);
  }

  @Override
  public <T extends PsiAnnotationMemberValue> T setDeclaredAttributeValue(@NonNls String attributeName, @Nullable T value) {
    throw new UnsupportedOperationException("light annotation does not support changes");
  }

  @Override
  public PsiAnnotationOwner getOwner() {
    return myOwner;
  }

  public void addAttribute(PsiNameValuePair pair) {
    if (pair instanceof GrAnnotationNameValuePair) {
      myAnnotationArgList.addAttribute((GrAnnotationNameValuePair)pair);
    }
    else {
      GrAnnotationMemberValue newValue = new AnnotationArgConverter().convert(pair.getValue());
      if (newValue == null) return;

      String name = pair.getName();
      addAttribute(name, newValue.getText());
    }
  }

  public void addAttribute(@NlsSafe @Nullable String name, @NlsSafe @NotNull String value) {
    GroovyPsiElementFactory factory = GroovyPsiElementFactory.getInstance(getProject());
    String text = name != null ? name + "=" + value : value;
    myAnnotationArgList.addAttribute(factory.createAnnotationAttribute(text, this));
  }

  private final class GrLightAnnotationArgumentList extends LightElement implements GrAnnotationArgumentList {
    private List<GrAnnotationNameValuePair> myAttributes = null;
    private GrAnnotationNameValuePair[] myCachedAttributes = GrAnnotationNameValuePair.EMPTY_ARRAY;


    @Override
    public PsiElement getContext() {
      return GrLightAnnotation.this;
    }

    private GrLightAnnotationArgumentList(@NotNull PsiManager manager, @NotNull Language language) {
      super(manager, language);
    }

    @Override
    public GrAnnotationNameValuePair @NotNull [] getAttributes() {
      if (myCachedAttributes == null) {
        assert myAttributes != null;
        myCachedAttributes = myAttributes.toArray(GrAnnotationNameValuePair.EMPTY_ARRAY);
      }
      return myCachedAttributes;
    }

    public void addAttribute(@NotNull GrAnnotationNameValuePair attribute) {
      if (myAttributes == null) myAttributes = new ArrayList<>();
      myAttributes.add(attribute);
      myCachedAttributes = null;
    }

    @Override
    public void accept(@NotNull GroovyElementVisitor visitor) {
      visitor.visitAnnotationArgumentList(this);
    }

    @Override
    public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
      if (myAttributes != null) {
        for (GrAnnotationNameValuePair attribute : myAttributes) {
          attribute.accept(visitor);
        }
      }
    }

    @Override
    public String toString() {
      return "light annotation argument list";
    }

    @Override
    public String getText() {
      if (myAttributes == null || myAttributes.isEmpty()) return "";

      StringBuilder buffer = new StringBuilder();
      buffer.append('(');

      for (GrAnnotationNameValuePair attribute : myAttributes) {
        buffer.append(attribute.getText());
        buffer.append(',');
      }
      buffer.deleteCharAt(buffer.length() - 1);
      buffer.append(')');
      return buffer.toString();
    }
  }
}
