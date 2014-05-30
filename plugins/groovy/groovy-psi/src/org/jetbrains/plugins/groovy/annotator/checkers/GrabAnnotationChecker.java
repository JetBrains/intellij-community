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
package org.jetbrains.plugins.groovy.annotator.checkers;

import com.intellij.lang.annotation.AnnotationHolder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.grape.GrabAnnos;
import org.jetbrains.plugins.groovy.lang.psi.api.auxiliary.modifiers.annotation.GrAnnotation;

/**
 * @author Max Medvedev
 */
public class GrabAnnotationChecker extends CustomAnnotationChecker {
  @Override
  public boolean checkArgumentList(@NotNull AnnotationHolder holder, @NotNull GrAnnotation annotation) {
    return GrabAnnos.GRAB_ANNO.equals(annotation.getQualifiedName()) ||
           GrabAnnos.GRAPES_ANNO.equals(annotation.getQualifiedName()) ||
           GrabAnnos.GRAB_EXCLUDE_ANNO.equals(annotation.getQualifiedName()) ||
           GrabAnnos.GRAB_RESOLVER_ANNO.equals(annotation.getQualifiedName());
  }
}
