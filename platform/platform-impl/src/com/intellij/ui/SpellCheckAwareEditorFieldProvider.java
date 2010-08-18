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

import com.intellij.openapi.project.Project;

/**
 * Defines common contract for building {@link EditorTextField} with spell checking support.
 *
 * @author Denis Zhdanov
 * @since Aug 18, 2010 1:37:55 PM
 */
public interface SpellCheckAwareEditorFieldProvider {

  /**
   * @param project   target project
   * @return          {@link EditorTextField} with spell checking support.
   */
  EditorTextField getEditorField(Project project);
}
