/*
 * Copyright 2000-2008 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.util;

import com.intellij.navigation.ItemPresentation;
import com.intellij.navigation.NavigationItem;
import com.intellij.openapi.editor.colors.TextAttributesKey;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.SearchScope;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author ilyas
 */
public abstract class GrDynamicImplicitElement extends GrImplicitVariableImpl implements GrImplicitVariable, ItemPresentation, NavigationItem {
  private String myContainingClassName;
  private final PsiFile myContainingFile;
  public final Project myProject;

  public GrDynamicImplicitElement(PsiManager manager, @NonNls String name, @NonNls String type, String containingClassName, PsiFile containingFile) {
    super(manager, name, type, null);
    myContainingClassName = containingClassName;
    myContainingFile = containingFile;
    myProject = myManager.getProject();
  }

  public ItemPresentation getPresentation() {
    return this;
  }

  public String getPresentableText() {
    return null;
  }

  @Nullable
  public String getLocationString() {
    return null;
  }

  @Nullable
  public TextAttributesKey getTextAttributesKey() {
    return null;
  }

  @Nullable
  public PsiClass getContainingPsiClassElement() {
    final PsiClassType containingClassType = myManager.getElementFactory().createTypeByFQClassName(myContainingClassName, myManager.getProject().getAllScope());

    return containingClassType.resolve();
  }

  @NotNull
  public SearchScope getUseScope() {
    return myManager.getProject().getProjectScope();
  }

  public PsiFile getContainingFile() {
    return myContainingFile;
  }

  public String getContainingClassName() {
    return myContainingClassName;
  }
}
