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

package org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.members;

import com.intellij.psi.PsiEnumConstant;
import com.intellij.util.ArrayFactory;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrArgumentList;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrConstructorCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrEnumConstantInitializer;

/**
 * @author: Dmitry.Krasilschikov
 * @date: 06.04.2007
 */
public interface GrEnumConstant extends GrField, GrConstructorCall, PsiEnumConstant {
  GrEnumConstant[] EMPTY_ARRAY = new GrEnumConstant[0];
  ArrayFactory<GrEnumConstant> ARRAY_FACTORY = new ArrayFactory<GrEnumConstant>() {
    @NotNull
    @Override
    public GrEnumConstant[] create(int count) {
      return new GrEnumConstant[count];
    }
  };

  @Override
  @Nullable
  GrEnumConstantInitializer getInitializingClass();

  @Override
  GrArgumentList getArgumentList();
}
