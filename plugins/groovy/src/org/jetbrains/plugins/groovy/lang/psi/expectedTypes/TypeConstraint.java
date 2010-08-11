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
package org.jetbrains.plugins.groovy.lang.psi.expectedTypes;

import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiType;
import com.intellij.psi.search.GlobalSearchScope;

/**
 * @author ven
 */
public abstract class TypeConstraint {
  public static final TypeConstraint[] EMPTY_ARRAY = new TypeConstraint[0];

  protected final PsiType myType;

  public abstract boolean satisfied(PsiType type, PsiManager manager, GlobalSearchScope scope);

  public abstract PsiType getDefaultType();

  protected TypeConstraint(PsiType type) {
    myType = type;
  }

  public PsiType getType() {
    return myType;
  }
}
