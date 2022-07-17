// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.refactoring.listeners;

import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolderBase;
import com.intellij.psi.PsiElement;
import com.intellij.usageView.UsageInfo;
import com.intellij.util.Function;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class RefactoringEventData extends UserDataHolderBase {
  public static final Key<Collection<? extends String>> CONFLICTS_KEY = Key.create("conflicts");
  public static final Key<PsiElement> PSI_ELEMENT_KEY = Key.create("element");
  public static final Key<String[]> STRING_PROPERTIES = Key.create("stringProperties");
  public static final Key<PsiElement[]> PSI_ELEMENT_ARRAY_KEY = Key.create("elementArray");
  public static final Key<Collection<? extends UsageInfo>> USAGE_INFOS_KEY = Key.create("usageInfos");

  public void addElement(PsiElement element) {
    putUserData(PSI_ELEMENT_KEY, element);
  }

  public <T> void addMembers(T[] members, Function<? super T, ? extends PsiElement> converter) {
    List<PsiElement> elements = new ArrayList<>();
    for (T info : members) {
      elements.add(converter.fun(info));
    }
    addElements(elements);
  }
  
  public void addElements(Collection<? extends PsiElement> elements) {
    putUserData(PSI_ELEMENT_ARRAY_KEY, elements.toArray(PsiElement.EMPTY_ARRAY));
  }
  
  public void addElements(PsiElement[] elements) {
    putUserData(PSI_ELEMENT_ARRAY_KEY, elements);
  }

  public void addUsages(Collection<? extends UsageInfo> usageInfos) {
    putUserData(USAGE_INFOS_KEY, usageInfos);
  }

  public void addStringProperties(String... properties) {
    putUserData(STRING_PROPERTIES, properties);
  }
}
