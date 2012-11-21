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

import com.android.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.DesignerEditorPanel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.util.ThrowableRunnable;
import icons.AndroidDesignerIcons;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class OrientationAction extends AnAction {
  private static final Icon myHorizontalIcon = AndroidDesignerIcons.LinearLayout;
  private static final Icon myVerticalIcon = AndroidDesignerIcons.LinearLayout2;
  private static final Icon myHorizontalOverrideIcon = AndroidDesignerIcons.LinearLayout3;

  private final DesignerEditorPanel myDesigner;
  private final List<RadComponent> myComponents;
  private boolean mySelection;

  public OrientationAction(DesignerEditorPanel designer, List<RadComponent> components, boolean horizontal, boolean override) {
    myDesigner = designer;
    myComponents = components;
    mySelection = horizontal;
    update(getTemplatePresentation(), override);
  }

  @Override
  public void actionPerformed(AnActionEvent e) {
    mySelection = !mySelection;
    update(e.getPresentation(), false);
    myDesigner.getToolProvider().execute(new ThrowableRunnable<Exception>() {
      @Override
      public void run() throws Exception {
        ApplicationManager.getApplication().runWriteAction(new Runnable() {
          @Override
          public void run() {
            String value = mySelection ? "horizontal" : "vertical";
            for (RadComponent component : myComponents) {
              ((RadViewComponent)component).getTag().setAttribute("orientation", SdkConstants.NS_RESOURCES, value);
            }
          }
        });
      }
    }, "Change attribute 'orientation'", true);
  }

  public void update(Presentation presentation, boolean override) {
    String text;
    Icon icon;

    if (override) {
      text = "Override orientation to horizontal";
      icon = myHorizontalOverrideIcon;
    }
    else {
      text = "Convert orientation to " + (mySelection ? "vertical" : "horizontal");
      icon = mySelection ? myHorizontalIcon : myVerticalIcon;
    }

    presentation.setText(text);
    presentation.setDescription(text);
    presentation.setIcon(icon);
  }
}
