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
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComponentWithBrowseButton;
import com.intellij.ui.TextAccessor;
import com.intellij.ui.TextFieldWithAutoCompletion;

import java.util.Collection;
import java.util.Collections;

/**
 * @author Bas Leijdekkers
 */
public class TextFieldWithAutoCompletionWithBrowseButton
  extends ComponentWithBrowseButton<TextFieldWithAutoCompletion<String>> implements TextAccessor {

  public TextFieldWithAutoCompletionWithBrowseButton(Project project) {
    super(TextFieldWithAutoCompletion.create(project, Collections.emptyList(), false, null), null);
  }

  @Override
  public String getText() {
    return getChildComponent().getText();
  }

  public void setAutoCompletionItems(Collection<String> items) {
    getChildComponent().setVariants(items);
  }

  @Override
  public void setText(String text) {
    getChildComponent().setText(text);
  }
}
