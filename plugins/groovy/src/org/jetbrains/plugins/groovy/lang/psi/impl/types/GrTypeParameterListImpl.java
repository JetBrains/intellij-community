/*
 * Copyright 2000-2007 JetBrains s.r.o.
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

package org.jetbrains.plugins.groovy.lang.psi.impl.types;

import com.intellij.lang.ASTNode;
import com.intellij.psi.PsiTypeParameter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameterList;
import org.jetbrains.plugins.groovy.lang.psi.api.types.GrTypeParameter;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiElementImpl;

/**
 * @author ilyas
 */
public class GrTypeParameterListImpl extends GroovyPsiElementImpl implements GrTypeParameterList {

  public GrTypeParameterListImpl(@NotNull ASTNode node) {
    super(node);
  }

  public String toString() {
    return "Type parameter list";
  }

  public GrTypeParameter[] getTypeParameters() {
    return findChildrenByClass(GrTypeParameter.class);
  }

  public int getTypeParameterIndex(PsiTypeParameter typeParameter) {
    final GrTypeParameter[] typeParameters = getTypeParameters();
    for (int i = 0; i < typeParameters.length; i++) {
      if (typeParameters[i].equals(typeParameter)) return i;
    }

    return -1;
  }
}
