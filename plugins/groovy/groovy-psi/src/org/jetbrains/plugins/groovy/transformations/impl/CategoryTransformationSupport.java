/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.transformations.impl;

import com.intellij.psi.PsiClassType;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members.GrMethod;
import org.jetbrains.plugins.groovy.lang.psi.impl.synthetic.GrReflectedMethodImpl;
import org.jetbrains.plugins.groovy.lang.psi.util.GdkMethodUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;
import org.jetbrains.plugins.groovy.transformations.TransformationContext;

public class CategoryTransformationSupport implements AstTransformationSupport {

  @Override
  public void applyTransformation(@NotNull TransformationContext context) {
    PsiClassType type = GdkMethodUtil.getCategoryType(context.getCodeClass());
    if (type == null) return;
    for (GrMethod method : context.getCodeClass().getCodeMethods()) {
      context.removeMethod(method);
      context.addMethods(GrReflectedMethodImpl.doCreateReflectedMethods(method, type, method.getParameters()));
    }
  }
}
