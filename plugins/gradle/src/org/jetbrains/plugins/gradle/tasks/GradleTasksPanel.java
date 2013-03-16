/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.tasks;

import com.intellij.execution.Location;
import com.intellij.execution.executors.DefaultDebugExecutor;
import com.intellij.execution.executors.DefaultRunExecutor;
import com.intellij.ide.DataManager;
import com.intellij.ide.ui.customization.CustomizationUtil;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.ui.IdeBorderFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.containers.ContainerUtilRt;
import com.intellij.util.ui.GridBag;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.config.GradleLocalSettings;
import org.jetbrains.plugins.gradle.config.GradleSettings;
import org.jetbrains.plugins.gradle.config.GradleToolWindowPanel;
import org.jetbrains.plugins.gradle.execution.GradleTaskLocation;
import org.jetbrains.plugins.gradle.model.gradle.GradleTaskDescriptor;
import org.jetbrains.plugins.gradle.ui.GradleDataKeys;
import org.jetbrains.plugins.gradle.util.GradleBundle;
import org.jetbrains.plugins.gradle.util.GradleConstants;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.Collection;
import java.util.List;

/**
 * @author Denis Zhdanov
 * @since 3/14/13 4:36 PM
 */
public class GradleTasksPanel extends GradleToolWindowPanel {

  @NotNull private final GradleTasksModel myRecentTasksModel = new GradleTasksModel();
  @NotNull private final GradleTasksList  myRecentTasksList  = new GradleTasksList(myRecentTasksModel);
  
  @NotNull private final GradleTasksModel myAllTasksModel = new GradleTasksModel();
  @NotNull private final GradleTasksList  myAllTasksList  = new GradleTasksList(myAllTasksModel);

  @NotNull private final GradleLocalSettings myLocalSettings;

  public GradleTasksPanel(@NotNull Project project) {
    super(project, GradleConstants.TOOL_WINDOW_TOOLBAR_PLACE);
    myLocalSettings = GradleLocalSettings.getInstance(project);
    initContent();
  }

  @NotNull
  @Override
  protected JComponent buildContent() {
    JPanel result = new JPanel(new GridBagLayout());
    result.setBorder(IdeBorderFactory.createEmptyBorder(8));
    result.setOpaque(false);
    
    myRecentTasksModel.clear();
    myRecentTasksModel.setTasks(myLocalSettings.getRecentTasks());
    myRecentTasksModel.ensureSize(Registry.intValue(GradleConstants.REGISTRY_RECENT_TASKS_NUMBER_KEY, 5));
    addListPanel(myRecentTasksList, result, GradleBundle.message("gradle.task.recent.title"), false);
    
    myAllTasksModel.clear();
    Collection<GradleTaskDescriptor> tasks = myLocalSettings.getAvailableTasks();
    if (!tasks.isEmpty()) {
      myAllTasksModel.setTasks(tasks);
    }
    addListPanel(myAllTasksList, result, GradleBundle.message("gradle.task.all.title"), true);

    return result;
  }

  private static void addListPanel(@NotNull GradleTasksList list, @NotNull JPanel parent, @NotNull String title, boolean fillY) {
    JPanel panel = new JPanel(new BorderLayout());
    panel.setOpaque(false);
    panel.setBorder(IdeBorderFactory.createTitledBorder(title));

    setupMouseListener(list);
    CustomizationUtil.installPopupHandler(list, GradleConstants.ACTION_GROUP_TASKS, GradleConstants.TASKS_CONTEXT_MENU_PLACE);
    list.setEmptyText(GradleBundle.message("gradle.text.loading"));
    JBScrollPane scrollPane = new JBScrollPane(list);
    scrollPane.setBorder(null);
    panel.add(scrollPane, BorderLayout.CENTER);
    parent.add(panel, new GridBag().fillCell().weightx(1).weighty(fillY ? 1 : 0).coverLine());
  }

  private static void setupMouseListener(@NotNull final JBList list) {
    list.addMouseListener(new MouseAdapter() {

      @Override
      public void mousePressed(MouseEvent e) {
        // Change list selection on right click. That allows to right-click target task node and run/debug it.
        if (!e.isPopupTrigger()) {
          return;
        }
        int i = list.locationToIndex(e.getPoint());
        if (i < 0) {
          return;
        }
        if (list.isSelectedIndex(i)) {
          return;
        }
        list.setSelectedIndex(i);
      }

      @Override
      public void mouseClicked(MouseEvent e) {
        if (e.getClickCount() < 2) {
          return;
        }

        GradleTaskDescriptor.Type taskType = getSelectedTaskType(list);
        final String actionId;
        if (taskType == GradleTaskDescriptor.Type.DEBUG) {
          actionId = DefaultDebugExecutor.getDebugExecutorInstance().getContextActionId();
        }
        else if (taskType == GradleTaskDescriptor.Type.RUN) {
          actionId = DefaultRunExecutor.getRunExecutorInstance().getContextActionId();
        }
        else if (Registry.is(GradleConstants.REGISTRY_DEBUG_ON_TASK_CLICK_KEY, false)) {
          actionId = DefaultDebugExecutor.getDebugExecutorInstance().getContextActionId();
        }
        else {
          actionId = DefaultRunExecutor.getRunExecutorInstance().getContextActionId();
        }
        if (StringUtil.isEmpty(actionId)) {
          return;
        }

        ActionManager actionManager = ActionManager.getInstance();
        AnAction action = actionManager.getAction(actionId);
        if (action == null) {
          return;
        }

        final Presentation presentation = new Presentation();
        DataContext dataContext = DataManager.getInstance().getDataContext(e.getComponent());
        final AnActionEvent event = new AnActionEvent(e, dataContext, GradleConstants.TASKS_LIST_PLACE, presentation, actionManager, 0);
        action.update(event);
        if (presentation.isEnabled()) {
          action.actionPerformed(event);
        }
      }
    });
  }
  
  @Override
  protected void updateContent() {
  }

  @Nullable
  @Override
  public Object getData(@NonNls String dataId) {
    if (GradleDataKeys.ALL_TASKS_MODEL.is(dataId)) {
      return myAllTasksModel;
    }
    else if (GradleDataKeys.RECENT_TASKS_MODEL.is(dataId)) {
      return myRecentTasksModel;
    }
    else if (Location.DATA_KEY.is(dataId)) {
      Location location = buildLocation();
      return location == null ? super.getData(dataId) : location;
    }
    else {
      return super.getData(dataId);
    }
  }

  @Nullable
  private Location buildLocation() {
    List<String> tasks = getSelectedTasks(myAllTasksList);
    if (tasks == null) {
      return null;
    }

    String gradleProjectPath = GradleSettings.getInstance(getProject()).getLinkedProjectPath();
    if (StringUtil.isEmpty(gradleProjectPath)) {
      return null;
    }
    
    assert gradleProjectPath != null;
    VirtualFile vFile = LocalFileSystem.getInstance().refreshAndFindFileByPath(gradleProjectPath);
    if (vFile == null) {
      return null;
    }

    PsiFile psiFile = PsiManager.getInstance(getProject()).findFile(vFile);
    if (psiFile == null) {
      return null;
    }
    
    return new GradleTaskLocation(getProject(), psiFile, tasks);
  }

  @Nullable
  private static GradleTaskDescriptor.Type getSelectedTaskType(@NotNull JBList list) {
    int[] indices = list.getSelectedIndices();
    if (indices == null || indices.length != 1) {
      return null;
    }
    return ((GradleTaskDescriptor)list.getModel().getElementAt(indices[0])).getType();
  }
  
  @Nullable
  private static List<String> getSelectedTasks(@NotNull JBList list) {
    int[] selectedIndices = list.getSelectedIndices();
    if (selectedIndices == null) {
      return null;
    }
    final List<String> tasks = ContainerUtilRt.newArrayList();
    for (int index : selectedIndices) {
      Object data = list.getModel().getElementAt(index);
      if (data instanceof GradleTaskDescriptor) {
        tasks.add(((GradleTaskDescriptor)data).getName());
      }
    }
    return tasks.isEmpty() ? null : tasks;
  }
}
