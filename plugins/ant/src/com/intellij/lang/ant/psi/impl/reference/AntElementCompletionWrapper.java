/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package com.intellij.lang.ant.psi.impl.reference;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.impl.AntElementImpl;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiNamedElement;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntElementCompletionWrapper extends AntElementImpl implements PsiNamedElement {

  private final String myName;
  private final Project myProject;
  private final AntElementRole myRole;

  public AntElementCompletionWrapper(final AntElement parent, final String name, @NotNull final Project project, final AntElementRole role) {
    super(parent, null);
    myName = name;
    myProject = project;
    myRole = role;
  }

  public final String getText() {
    return getName();
  }

  public boolean equals(final Object that) {
    if (this == that) {
      return true;
    }
    if (that instanceof AntElementCompletionWrapper) {
      return myName.equals(((AntElementCompletionWrapper)that).myName);
    }
    return false;
  }

  public int hashCode() {
    return myName.hashCode();
  }

  public final String getName() {
    return myName;
  }

  @NotNull
  public Project getProject() {
    return myProject;
  }

  public AntElementRole getRole() {
    return myRole;
  }

  public boolean isValid() {
    return true;
  }

  public boolean isPhysical() {
    return false;
  }

  public PsiElement setName(@NonNls @NotNull final String name) throws IncorrectOperationException {
    throw new IncorrectOperationException("Can't rename ant completion element");  
  }
}
