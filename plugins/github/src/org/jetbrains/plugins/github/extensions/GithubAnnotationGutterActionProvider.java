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
package org.jetbrains.plugins.github.extensions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.vcs.annotate.AnnotationGutterActionProvider;
import com.intellij.openapi.vcs.annotate.FileAnnotation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.GithubOpenInBrowserFromAnnotationActionGroup;

/**
 * @author Kirill Likhodedov
 */
public class GithubAnnotationGutterActionProvider implements AnnotationGutterActionProvider {

  @NotNull
  @Override
  public AnAction createAction(@NotNull FileAnnotation annotation) {
    return new GithubOpenInBrowserFromAnnotationActionGroup(annotation);
  }
}
