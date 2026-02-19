// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiModifier;
import com.intellij.psi.PsiVariable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.GrDynamicImplicitProperty;

public class DPropertyElement extends DItemElement {
  private GrDynamicImplicitProperty myPsi;

  //Do not use directly! Persistence component uses default constructor for deserializable
  @SuppressWarnings("UnusedDeclaration")
  public DPropertyElement() {
    super(null, null, null);
  }

  public DPropertyElement(Boolean isStatic, String name, String type) {
    super(isStatic, name, type);
  }

  @Override
  public void clearCache() {
    myPsi = null;
  }

  @Override
  public @NotNull PsiVariable getPsi(PsiManager manager, final String containingClassName) {
    if (myPsi != null) return myPsi;

    Boolean isStatic = isStatic();

    String type = getType();
    if (type == null || type.trim().isEmpty()) {
      type = CommonClassNames.JAVA_LANG_OBJECT;
    }
    myPsi = new GrDynamicImplicitProperty(manager, getName(), type, containingClassName);

    if (isStatic != null && isStatic.booleanValue()) {
      myPsi.getModifierList().addModifier(PsiModifier.STATIC);
    }

    return myPsi;
  }
}
