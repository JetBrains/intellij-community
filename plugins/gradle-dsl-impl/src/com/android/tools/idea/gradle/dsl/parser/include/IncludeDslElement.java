/*
 * Copyright (C) 2019 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.parser.include;

import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpressionList;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import com.android.tools.idea.gradle.dsl.parser.semantics.PropertiesElementDescription;
import com.intellij.psi.PsiElement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class IncludeDslElement extends GradlePropertiesDslElement {
  public static final PropertiesElementDescription<IncludeDslElement> INCLUDE =
    new PropertiesElementDescription<>("include", IncludeDslElement.class, IncludeDslElement::new);

  public IncludeDslElement(@Nullable GradleDslElement parent, @NotNull GradleNameElement name) {
    super(parent, null, name);
  }

  @Override
  @Nullable
  public PsiElement create() {
    return myParent != null ? myParent.create() : null;
  }

  public List<GradleDslSimpleExpression> getModules() {
    return getPropertyElements(GradleDslElement.class).stream().
      filter( e -> e instanceof GradleDslMethodCall || e instanceof GradleDslExpressionList || e instanceof GradleDslLiteral).
      flatMap(e -> (e instanceof GradleDslMethodCall) ?
                   ((GradleDslMethodCall)e).getArgumentsElement().getSimpleExpressions().stream() :
                   (e instanceof GradleDslExpressionList) ? ((GradleDslExpressionList)e).getSimpleExpressions().stream() :
                   new ArrayList<GradleDslSimpleExpression>(Collections.singleton((GradleDslLiteral)e)).stream()).collect(Collectors.toList());
  }

  public void removeModule(@NotNull Object value) {
    for (GradleDslSimpleExpression module : getModules()) {
      if (value.equals(module.getValue())) {
        if (module.getParent() instanceof GradleDslExpressionList) {
          GradleDslExpressionList parent = ((GradleDslExpressionList)module.getParent());
          parent.removeProperty(module);
        }
        else {
          super.removeProperty(module);
        }
        updateDependenciesOnRemoveElement(module);
        return;
      }
    }
  }

  public void replaceModulePath(@NotNull Object oldValue, @NotNull Object newValue) {
    for (GradleDslSimpleExpression module : getModules()) {
      if (oldValue.equals(module.getValue())) {
        module.setValue(newValue);
        return;
      }
    }
  }
}
