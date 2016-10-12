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
package org.jetbrains.plugins.groovy.lang.resolve.ast.builder;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.PsiAnnotation;
import com.intellij.psi.PsiClass;
import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.lang.psi.impl.GrAnnotationUtil;
import org.jetbrains.plugins.groovy.transformations.AstTransformationSupport;

public abstract class BuilderAnnotationContributor implements AstTransformationSupport {

  public static final String BUILDER_PACKAGE = "groovy.transform.builder";
  public static final String BUILDER_FQN = BUILDER_PACKAGE + ".Builder";
  public static final String ORIGIN_INFO = "via @Builder";
  public static final String STRATEGY_ATTRIBUTE = "builderStrategy";

  @Contract("null, _ -> false")
  public static boolean isApplicable(@Nullable PsiAnnotation annotation, @NotNull String strategy) {
    if (annotation == null) return false;
    PsiClass aClass = GrAnnotationUtil.inferClassAttribute(annotation, STRATEGY_ATTRIBUTE);
    if (aClass == null) return false;
    return StringUtil.getQualifiedName(BUILDER_PACKAGE, strategy).equals(aClass.getQualifiedName());
  }
}
