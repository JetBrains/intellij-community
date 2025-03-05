// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.xdebugger.stepping;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.psi.PsiNamedElement;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
* @author Maxim.Mossienko
*/
public class PsiBackedSmartStepIntoVariant<T extends PsiNamedElement & NavigationItem> extends XSmartStepIntoVariant {
  private final T myElement;
  private final ItemPresentation myPresentation;

  public PsiBackedSmartStepIntoVariant(@NotNull T element) {
    myElement = element;
    myPresentation = element.getPresentation();
    assert myPresentation != null: "Invalid presentation:" + myElement;
  }

  @Override
  public String getText() {
    String location = myPresentation.getLocationString();
    return myPresentation.getPresentableText() + (location != null ? " " + location: "");
  }

  @Override
  public Icon getIcon() {
    return myPresentation.getIcon(false);
  }

  public @NotNull T getElement() {
    return myElement;
  }
}
