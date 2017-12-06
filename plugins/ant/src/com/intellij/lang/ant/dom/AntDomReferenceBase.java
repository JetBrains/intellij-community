/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

  public boolean shouldBeSkippedByAnnotator() {
    return myShouldBeSkippedByAnnotator;
  }

  public void setShouldBeSkippedByAnnotator(boolean value) {
    myShouldBeSkippedByAnnotator = true;
  }
  
  @Nullable 
  public static DomElement toDomElement(PsiElement resolve) {
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
