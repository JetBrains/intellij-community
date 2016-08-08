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
package org.jetbrains.plugins.groovy.actions.generate.accessors;

import com.intellij.codeInsight.generation.EncapsulatableClassMember;
import com.intellij.codeInsight.generation.GenerateAccessorProviderRegistrar;
import com.intellij.codeInsight.generation.GenerateGetterAndSetterHandler;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiEnumConstant;
import com.intellij.psi.PsiField;
import com.intellij.util.NotNullFunction;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.actions.generate.GrBaseGenerateAction;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.GrField;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.typedef.GrTypeDefinition;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * @author Max Medvedev
 */
public class GroovyGenerateGetterSetterAction extends GrBaseGenerateAction {
  public GroovyGenerateGetterSetterAction() {
    super(new GenerateGetterAndSetterHandler());
  }

  static {
    GenerateAccessorProviderRegistrar.registerProvider(s -> {
      if (!(s instanceof GrTypeDefinition)) return Collections.emptyList();
      final List<EncapsulatableClassMember> result = new ArrayList<>();
      for (PsiField field : s.getFields()) {
        if (!(field instanceof PsiEnumConstant) && field instanceof GrField) {
          result.add(new GrFieldMember((GrField)field));
        }
      }
      return result;
    });
  }

}
