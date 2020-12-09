/*
 * Copyright (C) 2016 The Android Open Source Project
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
package com.android.tools.idea.gradle.dsl.model.dependencies;

import static com.android.tools.idea.gradle.dsl.api.ext.PropertyType.REGULAR;

import com.android.tools.idea.gradle.dsl.api.dependencies.FileDependencyModel;
import com.android.tools.idea.gradle.dsl.api.ext.ResolvedPropertyModel;
import com.android.tools.idea.gradle.dsl.model.ext.GradlePropertyModelBuilder;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslLiteral;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslMethodCall;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleDslSimpleExpression;
import com.android.tools.idea.gradle.dsl.parser.elements.GradleNameElement;
import com.android.tools.idea.gradle.dsl.parser.elements.GradlePropertiesDslElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class FileDependencyModelImpl extends DependencyModelImpl implements FileDependencyModel {
  @NonNls public static final String FILES = "files";

  @NotNull private GradleDslSimpleExpression myFileDslExpression;

  static Collection<FileDependencyModel> create(@NotNull String configurationName,
                                                @NotNull GradleDslMethodCall methodCall,
                                                @NotNull Maintainer maintainer) {
    List<FileDependencyModel> result = new ArrayList<>();
    Maintainer argumentMaintainer;
    if (maintainer == DependenciesModelImpl.Maintainers.SINGLE_ITEM_MAINTAINER) {
      argumentMaintainer = DependenciesModelImpl.Maintainers.DEEP_SINGLE_ITEM_MAINTAINER;
    }
    else if (maintainer == DependenciesModelImpl.Maintainers.ARGUMENT_LIST_MAINTAINER) {
      argumentMaintainer = DependenciesModelImpl.Maintainers.DEEP_ARGUMENT_LIST_MAINTAINER;
    }
    else if (maintainer == DependenciesModelImpl.Maintainers.EXPRESSION_LIST_MAINTAINER) {
      argumentMaintainer = DependenciesModelImpl.Maintainers.DEEP_EXPRESSION_LIST_MAINTAINER;
    }
    else {
      throw new IllegalStateException();
    }
    if (FILES.equals(methodCall.getMethodName())) {
      List<GradleDslExpression> arguments = methodCall.getArguments();
      for (GradleDslElement argument : arguments) {
        if (argument instanceof GradleDslSimpleExpression) {
          result.add(new FileDependencyModelImpl(configurationName, (GradleDslSimpleExpression)argument, argumentMaintainer));
        }
      }
    }
    return result;
  }

  static void createNew(@NotNull GradlePropertiesDslElement parent,
                        @NotNull String configurationName,
                        @NotNull String file) {
    GradleNameElement name = GradleNameElement.create(configurationName);
    GradleDslMethodCall methodCall = new GradleDslMethodCall(parent, name, FILES);
    GradleDslLiteral fileDslLiteral = new GradleDslLiteral(methodCall, GradleNameElement.empty());
    fileDslLiteral.setElementType(REGULAR);
    fileDslLiteral.setValue(file);
    methodCall.addNewArgument(fileDslLiteral);
    parent.setNewElement(methodCall);
  }

  private FileDependencyModelImpl(@NotNull String configurationName,
                                  @NotNull GradleDslSimpleExpression fileDslExpression,
                                  @NotNull Maintainer maintainer) {
    super(configurationName, maintainer);
    myFileDslExpression = fileDslExpression;
  }

  @Override
  @NotNull
  protected GradleDslElement getDslElement() {
    return myFileDslExpression;
  }

  @Override
  void setDslElement(@NotNull GradleDslElement dslElement) {
    myFileDslExpression = (GradleDslSimpleExpression)dslElement;
  }

  @Override
  @NotNull
  public ResolvedPropertyModel file() {
    return GradlePropertyModelBuilder.create(myFileDslExpression).buildResolved();
  }
}
