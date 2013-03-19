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

import com.intellij.execution.Executor;
import com.intellij.execution.ExecutorRegistry;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.components.JBList;
import icons.GradleIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.gradle.GradleTaskDescriptor;

import javax.swing.*;
import java.awt.*;

/**
 * @author Denis Zhdanov
 * @since 3/15/13 7:38 PM
 */
public class GradleTasksList extends JBList {

  @NotNull private static final MyRenderer RENDERER       = new MyRenderer();
  @NotNull private static final JLabel     EMPTY_RENDERER = new JLabel(" ");

  public GradleTasksList(@NotNull GradleTasksModel model) {
    super(model);
    setCellRenderer(RENDERER);
  }

  private static class MyRenderer extends DefaultListCellRenderer {

    @Override
    public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
      if (value instanceof GradleTasksModel.MyEmptyDescriptor) {
        return EMPTY_RENDERER;
      }
      else if (value instanceof GradleTaskDescriptor) {
        GradleTaskDescriptor descriptor = (GradleTaskDescriptor)value;
        setText(descriptor.getName());
        Icon icon = null;
        String executorId = descriptor.getExecutorId();
        if (!StringUtil.isEmpty(executorId)) {
          Executor executor = ExecutorRegistry.getInstance().getExecutorById(executorId);
          if (executor != null) {
            icon = executor.getIcon();
          }
        }

        if (icon == null) {
          icon = GradleIcons.Task;
        }
        setIcon(icon);
      }
      return super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
    }

    @Override
    public void setIcon(Icon icon) {
      if (icon != null) {
        // Don't allow to reset icon.
        super.setIcon(icon);
      }
    }
  }

  
}
