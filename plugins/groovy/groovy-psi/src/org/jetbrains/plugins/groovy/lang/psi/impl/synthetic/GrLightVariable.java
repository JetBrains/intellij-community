// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.lang.psi.impl.synthetic;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.tree.LeafElement;
import com.intellij.psi.search.LocalSearchScope;
import com.intellij.psi.search.SearchScope;
import com.intellij.psi.xml.XmlAttributeValue;
import com.intellij.psi.xml.XmlToken;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentLabel;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.util.GroovyPropertyUtils;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

public class GrLightVariable extends GrImplicitVariableImpl implements NavigatablePsiElement {

  private final List<PsiElement> myDeclarations;

  private Object myCreatorKey;

  public GrLightVariable(PsiManager manager,
                         @NlsSafe String name,
                         @NlsSafe @NotNull String type,
                         @NotNull PsiElement navigationElement) {
    this(manager, name, type, Collections.singletonList(navigationElement), getDeclarationScope(navigationElement));
  }

  public GrLightVariable(PsiManager manager,
                         @NlsSafe String name,
                         @NlsSafe @NotNull String type,
                         @NotNull List<PsiElement> declarations,
                         @NotNull PsiElement scope) {
    this(manager, name, JavaPsiFacade.getElementFactory(manager.getProject()).createTypeFromText(type, scope), declarations, scope);
  }

  public GrLightVariable(PsiManager manager,
                         @NlsSafe String name,
                         @NotNull PsiType type,
                         @NotNull PsiElement navigationElement) {
    this(manager, name, type, Collections.singletonList(navigationElement), getDeclarationScope(navigationElement));
  }

  public GrLightVariable(PsiManager manager,
                         @NlsSafe String name,
                         @NotNull PsiType type,
                         @NotNull List<PsiElement> declarations,
                         @NotNull PsiElement scope) {
    super(manager, new GrLightIdentifier(manager, name), type, false, scope);

    myDeclarations = declarations;
    if (!myDeclarations.isEmpty()) {
      setNavigationElement(myDeclarations.get(0));
    }
  }

  private static PsiElement getDeclarationScope(PsiElement navigationElement) {
    return navigationElement.getContainingFile();
  }

  @Override
  public boolean isWritable() {
    return getNavigationElement() != this;
  }

  @Override
  public PsiFile getContainingFile() {
    if (!myDeclarations.isEmpty()) {
      return myDeclarations.get(0).getContainingFile();
    }
    else {
      return getDeclarationScope().getContainingFile();
    }
  }

  @Override
  public boolean isValid() {
    for (PsiElement declaration : myDeclarations) {
      if (!declaration.isValid()) return false;
    }

    return true;
  }

  @Override
  public @NotNull SearchScope getUseScope() {
    return new LocalSearchScope(getDeclarationScope());
  }

  @Override
  public boolean isEquivalentTo(PsiElement another) {
    return myDeclarations.contains(another) || super.isEquivalentTo(another);
  }

  @Override
  public PsiElement setName(@NotNull String name) throws IncorrectOperationException {
    for (PsiElement declaration : myDeclarations) {
      if (declaration instanceof PsiNamedElement) {
        if (declaration instanceof PsiMethod) {
          name = GroovyPropertyUtils.getGetterNameNonBoolean(name);
        }
        ((PsiNamedElement)declaration).setName(name);
      }
      else if (declaration instanceof GrArgumentLabel) {
        ((GrArgumentLabel)declaration).setName(name);
      }
      else if (declaration instanceof XmlAttributeValue) {
        PsiElement leftQuote = declaration.getFirstChild();

        if (!(leftQuote instanceof XmlToken)) continue;

        PsiElement textToken = leftQuote.getNextSibling();

        if (!(textToken instanceof XmlToken)) continue;

        PsiElement rightQuote = textToken.getNextSibling();

        if (!(rightQuote instanceof XmlToken) || rightQuote.getNextSibling() != null) continue;

        ((LeafElement)textToken).replaceWithText(name);
      }
      else if (declaration instanceof GrReferenceExpression) {
        ((GrReferenceExpression)declaration).handleElementRename(name);
      }
    }

    return getNameIdentifier().replace(new GrLightIdentifier(myManager, name));
  }

  public Object getCreatorKey() {
    return myCreatorKey;
  }

  public void setCreatorKey(Object creatorKey) {
    myCreatorKey = creatorKey;
  }

  public List<PsiElement> getDeclarations() {
    return myDeclarations;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    GrLightVariable variable = (GrLightVariable)o;
    return Objects.equals(getName(), variable.getName()) &&
           Objects.equals(myDeclarations, variable.myDeclarations) &&
           Objects.equals(myCreatorKey, variable.myCreatorKey);
  }

  @Override
  public int hashCode() {
    return Objects.hash(getName(), myDeclarations, myCreatorKey);
  }
}
