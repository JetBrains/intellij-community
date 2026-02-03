// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.dom;

import com.intellij.openapi.util.TextRange;
import com.intellij.pom.PomTarget;
import com.intellij.pom.PomTargetPsiElement;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReferenceBase;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomTarget;
import com.intellij.util.xml.DomUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene Zhuravlev
 */
public abstract class AntDomReferenceBase extends PsiReferenceBase<PsiElement> implements AntDomReference{
  private boolean myShouldBeSkippedByAnnotator;
  protected AntDomReferenceBase(PsiElement element, TextRange range, boolean soft) {
    super(element, range, soft);
  }

  protected AntDomReferenceBase(PsiElement element, TextRange range) {
    super(element, range);
  }

  protected AntDomReferenceBase(PsiElement element, boolean soft) {
    super(element, soft);
  }

  protected AntDomReferenceBase(@NotNull PsiElement element) {
    super(element);
  }

  @Override
  public boolean shouldBeSkippedByAnnotator() {
    return myShouldBeSkippedByAnnotator;
  }

  @Override
  public void setShouldBeSkippedByAnnotator(boolean value) {
    myShouldBeSkippedByAnnotator = true;
  }

  public static @Nullable DomElement toDomElement(PsiElement resolve) {
    if (resolve instanceof PomTargetPsiElement) {
      final PomTarget target = ((PomTargetPsiElement)resolve).getTarget();
      if(target instanceof DomTarget) {
        return ((DomTarget)target).getDomElement();
      }
      return null;
    }
    return DomUtil.getDomElement(resolve);
  }

}
