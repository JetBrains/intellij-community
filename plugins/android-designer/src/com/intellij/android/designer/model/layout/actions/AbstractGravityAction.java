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
package com.intellij.android.designer.model.layout.actions;

import com.intellij.android.designer.model.layout.Gravity;
import com.intellij.designer.actions.AbstractComboBoxAction;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ThrowableRunnable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractGravityAction<T> extends AbstractComboBoxAction<T> {
  private final DesignerEditorPanel myDesigner;
  protected final List<RadComponent> myComponents;

  public AbstractGravityAction(DesignerEditorPanel designer, List<RadComponent> components) {
    myDesigner = designer;
    myComponents = components;

    Presentation presentation = getTemplatePresentation();
    presentation.setDescription("Gravity");
    presentation.setIcon(Gravity.ICON);
  }

  protected final void execute(final Runnable command) {
    myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            command.run();
          }
        });
      }
    }, "Change attribute 'gravity'", true);
  }
}