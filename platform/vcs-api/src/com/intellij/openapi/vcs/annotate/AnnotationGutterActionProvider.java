/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.annotate;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.extensions.ExtensionPointName;
import org.jetbrains.annotations.NotNull;

/**
 * Implement this to add an additional custom action to the annotation gutter popup (invoked by right-click to an annotation).
 */
public interface AnnotationGutterActionProvider {

  ExtensionPointName<AnnotationGutterActionProvider> EP_NAME = ExtensionPointName.create("com.intellij.vcsAnnotationGutterActionProvider");

  /**
   * Create an action that will be added to the annotation gutter popup.
   *
   * @param annotation annotation which is currently shown on the gutter.
   * @return new action that can be invoked from the annotation gutter popup.
   */
  @NotNull
  AnAction createAction(@NotNull FileAnnotation annotation);

}
