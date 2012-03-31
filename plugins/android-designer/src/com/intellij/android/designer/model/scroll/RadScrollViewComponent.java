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
package com.intellij.android.designer.model.scroll;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.IconLoader;
import com.intellij.util.ThrowableRunnable;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadScrollViewComponent extends RadViewComponent {
  private static final Icon SCROLL = IconLoader.getIcon("/com/intellij/android/designer/icons/scroll_to_h_v.png");

  @Override
  public void addSelectionActions(DesignerEditorPanel designer,
                                  DefaultActionGroup actionGroup,
                                  JComponent shortcuts,
                                  List<RadComponent> selection) {
    if (selection.size() == 1) {
      addScrollAction(designer, actionGroup, shortcuts, this);
    }
  }

  public static void addScrollAction(final DesignerEditorPanel designer,
                                     DefaultActionGroup actionGroup,
                                     JComponent shortcuts,
                                     final RadViewComponent scrollContainer) {
    String text = "Convert ScrollView to horizontal/vertical";
    actionGroup.add(new AnAction(text, text, SCROLL) {
      @Override
      public void actionPerformed(AnActionEvent e) {
        designer.getToolProvider().execute(new ThrowableRunnable<Exception>() {
          @Override
          public void run() throws Exception {
            MetaManager manager = ViewsMetaManager.getInstance(scrollContainer.getTag().getProject());
            MetaModel vScrollView = manager.getModelByTag("ScrollView");
            MetaModel hScrollView = manager.getModelByTag("HorizontalScrollView");

            scrollContainer.setMetaModel(scrollContainer.getMetaModel() == vScrollView ? hScrollView : vScrollView);

            ApplicationManager.getApplication().runWriteAction(new Runnable() {
              @Override
              public void run() {
                scrollContainer.getTag().setName(scrollContainer.getMetaModel().getTag());
              }
            });

            // TODO: maybe reload properties
          }
        }, true);
      }
    });
  }
}