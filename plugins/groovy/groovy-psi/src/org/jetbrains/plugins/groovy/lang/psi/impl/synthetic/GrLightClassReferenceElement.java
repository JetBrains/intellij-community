// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.TextRange;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiType;
import com.intellij.psi.impl.light.LightElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.GroovyLanguage;
import org.jetbrains.plugins.groovy.lang.psi.GroovyElementVisitor;
import org.jetbrains.plugins.groovy.lang.psi.api.GroovyResolveResult;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.psi.api.types.CodeReferenceKind;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrCodeReferenceElement;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeArgumentList;
import org.jetbrains.plugins.groovy.lang.resolve.ElementResolveResult;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Maxim.Medvedev
 */
public class GrLightClassReferenceElement extends LightElement implements GrCodeReferenceElement {
  @NotNull
  private final String myClassName;
  private final String myText;
  private final PsiElement myContext;

  public GrLightClassReferenceElement(@NotNull String className, @NotNull String text, PsiElement context) {
    super(context.getManager(), GroovyLanguage.INSTANCE);
    myClassName = className;
    myText = text;
    myContext = context;
  }

  public GrLightClassReferenceElement(PsiClass aClass, PsiElement context) {
    this(aClass.getQualifiedName() != null ? aClass.getQualifiedName() : aClass.getName(), aClass.getName(), context);
  }

  @Override
  public String getReferenceName() {
    return myClassName;
  }

  @Nullable
  @Override
  public String getQualifiedReferenceName() {
    return myClassName;
  }

  @NotNull
  @Override
  public Collection<? extends GroovyResolveResult> resolve(boolean incomplete) {
    PsiClass clazz = JavaPsiFacade.getInstance(getProject()).findClass(myClassName, myContext.getResolveScope());
    return clazz == null ? Collections.emptyList() : Collections.singletonList(new ElementResolveResult<>(clazz));
  }

  @Override
  public PsiType @NotNull [] getTypeArguments() {
    return PsiType.EMPTY_ARRAY;
  }

  @Override
  public GrTypeArgumentList getTypeArgumentList() {
    return null;
  }

  @Override
  public GrCodeReferenceElement getQualifier() {
    return null;
  }

  @Override
  public void setQualifier(@Nullable GrCodeReferenceElement grCodeReferenceElement) {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement getReferenceNameElement() {
    return null;
  }

  @Override
  public boolean isQualified() {
    return false;
  }

  @Override
  public void accept(@NotNull GroovyElementVisitor visitor) {
    //todo ???
  }

  @Override
  public void acceptChildren(@NotNull GroovyElementVisitor visitor) {
  }

  @Override
  public String toString() {
    return "light reference element";
  }

  @NotNull
  @Override
  public PsiElement getElement() {
    return this;
  }

  @NotNull
  @Override
  public TextRange getRangeInElement() {
    return new TextRange(0, getTextLength());
  }

  @NotNull
  @Override
  public String getCanonicalText() {
    final PsiElement resolved = resolve();
    if (resolved instanceof PsiClass) return ((PsiClass)resolved).getQualifiedName();
    return myClassName;
  }

  @Override
  public PsiElement handleElementRename(@NotNull String newElementName) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public PsiElement bindToElement(@NotNull PsiElement element) throws IncorrectOperationException {
    throw new UnsupportedOperationException();
  }

  @Override
  public boolean isReferenceTo(@NotNull PsiElement element) {
    return myManager.areElementsEquivalent(element, resolve());
  }

  @Override
  public boolean isSoft() {
    return false;
  }

  @Override
  public String getText() {
    return myText;
  }

  @NotNull
  @Override
  public CodeReferenceKind getKind() {
    return CodeReferenceKind.REFERENCE;
  }

  @Override
  public GrAnnotation @NotNull [] getAnnotations() {
    return GrAnnotation.EMPTY_ARRAY;
  }
}
