/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.android.actions;

import com.android.ddmlib.AndroidDebugBridge;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidConnectDebuggerAction extends AnAction {


  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    assert project != null;

    if (!AndroidSdkUtils.activateDdmsIfNecessary(project, new Computable<AndroidDebugBridge>() {
      @Nullable
      @Override
      public AndroidDebugBridge compute() {
        return AndroidSdkUtils.getDebugBridge(project);
      }
    })) {
      return;
    }

    final AndroidProcessChooserDialog dialog = new AndroidProcessChooserDialog(project);
    dialog.show();
  }


  @Override
  public void update(AnActionEvent e) {
    super.update(e);
    final Project project = e.getData(PlatformDataKeys.PROJECT);
    e.getPresentation().setVisible(project != null &&
                                   ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID).size() > 0);
  }
}
