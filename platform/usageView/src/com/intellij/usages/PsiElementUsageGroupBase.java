// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.usages;

import com.intellij.navigation.NavigationItem;
import com.intellij.navigation.NavigationItemFileStatus;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.psi.PsiElement;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.util.Objects;

/**
 * @author Maxim.Mossienko
 */
public class PsiElementUsageGroupBase<T extends PsiElement & NavigationItem> implements UsageGroup, NamedPresentably {
  private final SmartPsiElementPointer<T> myElementPointer;
  private final @NlsSafe String myName;
  private final Icon myIcon;

  public PsiElementUsageGroupBase(@NotNull T element, Icon icon, @NotNull @NlsSafe String name) {
    myName = name;
    myElementPointer = SmartPointerManager.getInstance(element.getProject()).createSmartPsiElementPointer(element);
    myIcon = icon;
  }

  public PsiElementUsageGroupBase(@NotNull T element, Icon icon) {
    this(element, icon, getPresentationName(element));
  }

  public PsiElementUsageGroupBase(@NotNull T element) {
    this(element, element.getIcon(0));
  }

  public PsiElementUsageGroupBase(@NotNull T element, @NotNull String name) {
    this(element, element.getIcon(0), name);
  }

  private static <T extends PsiElement & NavigationItem> @NotNull String getPresentationName(@NotNull T element) {
    String name = element.getName();
    return name != null ? name : "<anonymous>";
  }

  @Override
  public Icon getIcon() {
    return myIcon;
  }

  public T getElement() {
    return myElementPointer.getElement();
  }

  @Override
  public @NotNull String getPresentableGroupText() {
    return myName;
  }

  @Override
  public FileStatus getFileStatus() {
    return isValid() ? NavigationItemFileStatus.get(getElement()) : null;
  }

  @Override
  public boolean isValid() {
    final T element = getElement();
    return element != null && element.isValid();
  }

  @Override
  public void navigate(boolean focus) throws UnsupportedOperationException {
    if (canNavigate()) {
      getElement().navigate(focus);
    }
  }

  @Override
  public boolean canNavigate() {
    return isValid();
  }

  @Override
  public boolean canNavigateToSource() {
    return canNavigate();
  }

  @Override
  public int compareTo(final @NotNull UsageGroup o) {
    String name;
    if (o instanceof NamedPresentably) {
      name = ((NamedPresentably)o).getPresentableName();
    } else {
      name = o.getPresentableGroupText();
    }
    return myName.compareToIgnoreCase(name);
  }

  @Override
  public boolean equals(final Object obj) {
    if (!(obj instanceof PsiElementUsageGroupBase group)) return false;
    if (isValid() && group.isValid()) {
      return getElement().getManager().areElementsEquivalent(getElement(), group.getElement());
    }
    return Objects.equals(myName, group.myName);
  }

  @Override
  public int hashCode() {
    return myName.hashCode();
  }

  @Override
  public @NotNull String getPresentableName() {
    return myName;
  }
}
