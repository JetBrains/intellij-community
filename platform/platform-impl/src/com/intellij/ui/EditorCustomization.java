/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.lang.Language;
import com.intellij.openapi.editor.ex.EditorEx;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Defines contract for functionality that is able to customize editors.
 * <p/>
 * Such customizations can be then passed to {@link EditorTextFieldProvider#getEditorField(Language, Project, Iterable)} to get editor
 * with all necessary features applied or disabled.
 *
 * @author Denis Zhdanov
 * @since Aug 20, 2010 4:26:04 PM
 */
public interface EditorCustomization {

  /**
   * Applies this customization to the given editor.
   * Subclasses should apply their customizations to the editor in this method.
   *
   * @param editor The editor to customize
   */
  void customize(@NotNull EditorEx editor);

}
