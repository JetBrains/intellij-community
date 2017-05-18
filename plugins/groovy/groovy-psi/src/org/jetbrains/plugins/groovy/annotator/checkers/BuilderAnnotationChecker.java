/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.GroovyBundle;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.BuilderAnnotationContributor;
import org.jetbrains.plugins.groovy.lang.resolve.ast.builder.strategy.SimpleBuilderStrategySupport;

/**
 * Annotation attribute 'includeSuperProperties' not supported by groovy.transform.builder.SimpleStrategy
 */
public class BuilderAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkApplicability(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    if (!BuilderAnnotationContributor.BUILDER_FQN.equals(annotation.getQualifiedName())) return false;

    if(BuilderAnnotationContributor.isApplicable(annotation, SimpleBuilderStrategySupport.SIMPLE_STRATEGY_NAME) &&
       BuilderAnnotationContributor.isIncludeSuperProperties(annotation)) {
      holder.createErrorAnnotation(annotation, GroovyBundle.message("builder.annotation.not.support.super.for.simple.strategy"));
      return true;
    }

    return false;
  }
}
