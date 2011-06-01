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

package org.jetbrains.android.logcat;

import com.intellij.CommonBundle;
import com.intellij.facet.ProjectFacetManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowFactory;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.android.util.AndroidUtils;

import javax.swing.*;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidLogcatToolWindowFactory implements ToolWindowFactory {
  public static final String TOOL_WINDOW_ID = AndroidBundle.message("android.logcat.title");

  public void createToolWindowContent(final Project project, final ToolWindow toolWindow) {
    toolWindow.setSplitMode(true, null);
    toolWindow.setIcon(AndroidUtils.ANDROID_ICON);
    toolWindow.setAvailable(true, null);
    toolWindow.setToHideOnEmptyContent(true);
    toolWindow.setTitle(TOOL_WINDOW_ID);

    final AndroidLogcatToolWindowView view = new AndroidLogcatToolWindowView(project) {
      @Override
      protected boolean isActive() {
        return toolWindow.isVisible();
      }
    };
    final ToolWindowManagerEx toolWindowManager = ToolWindowManagerEx.getInstanceEx(project);
    toolWindowManager.addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      boolean myToolWindowVisible;

      @Override
      public void stateChanged() {
        ToolWindow window = toolWindowManager.getToolWindow(TOOL_WINDOW_ID);
        if (window != null) {
          boolean visible = window.isVisible();
          if (visible != myToolWindowVisible) {
            myToolWindowVisible = visible;
            view.activate();
            if (visible) {
              checkFacetAndSdk(project);
            }
          }
        }
      }
    });
    JPanel contentPanel = view.getContentPanel();
    final ContentManager contentManager = toolWindow.getContentManager();
    final Content content = contentManager.getFactory().createContent(contentPanel, null, false);
    content.setDisposer(view);
    content.setCloseable(false);
    content.setPreferredFocusableComponent(contentPanel);
    contentManager.addContent(content);
    contentManager.setSelectedContent(content, true);
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      public void run() {
        view.activate();
      }
    });
  }

  private static void checkFacetAndSdk(Project project) {
    List<AndroidFacet> facets = ProjectFacetManager.getInstance(project).getFacets(AndroidFacet.ID);
    if (facets.size() == 0) {
      Messages.showErrorDialog(project, AndroidBundle.message("android.logcat.no.android.facets.error"), CommonBundle.getErrorTitle());
      return;
    }

    AndroidFacet facet = facets.get(0);
    AndroidPlatform platform = facet.getConfiguration().getAndroidPlatform();
    if (platform == null) {
      Messages.showErrorDialog(project, AndroidBundle.message("specify.platform.error"), CommonBundle.getErrorTitle());
    }
  }
}
