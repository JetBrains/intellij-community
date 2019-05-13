/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.refactoring.extract.method;

import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiModifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMember;
import org.jetbrains.plugins.groovy.refactoring.extract.ExtractInfoHelperBase;
import org.jetbrains.plugins.groovy.refactoring.extract.InitialInfo;

/**
 * @author ilyas
 */
public class ExtractMethodInfoHelper extends ExtractInfoHelperBase {

  private final boolean myIsStatic;
  private boolean mySpecifyType = true;
  private String myVisibility;
  private String myName;
  private final PsiClass myOwner;
  private boolean myForceReturn;

  public ExtractMethodInfoHelper(InitialInfo initialInfo, String name, PsiClass owner, boolean forceReturn) {
    super(initialInfo);
    myOwner = owner;
    myForceReturn = forceReturn;

    myVisibility = PsiModifier.PRIVATE;
    myName = name;

    myIsStatic = canBeStatic(initialInfo.getContext());
  }

  public boolean isStatic() {
    return myIsStatic;
  }

  public String getVisibility() {
    return myVisibility;
  }

  public void setVisibility(String visibility) {
    myVisibility = visibility;
  }

  public boolean specifyType() {
    return mySpecifyType;
  }

  public void setSpecifyType(boolean specifyType) {
    mySpecifyType = specifyType;
  }

  @Override
  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  @NotNull
  public PsiClass getOwner() {
    return myOwner;
  }

  public void setForceReturn(boolean isForceReturn) {
    myForceReturn = isForceReturn;
  }

  private static boolean canBeStatic(PsiElement statement) {
    PsiElement parent = statement.getParent();
    while (parent != null && !(parent instanceof PsiFile)) {
      if (parent instanceof GrMember) {
        return ((GrMember) parent).hasModifierProperty(PsiModifier.STATIC);
      }
      parent = parent.getParent();
    }
    return false;
  }

  @Override
  public boolean isForceReturn() {
    return myForceReturn;
  }
}
