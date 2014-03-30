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
package git4idea;

import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Kirill Likhodedov
 */
public class MessageManager {

  public static MessageManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, MessageManager.class);
  }

  @Messages.YesNoResult
  public static int showYesNoDialog(Project project, String description, String title, String yesText, String noText, @Nullable Icon icon) {
    return getInstance(project).doShowYesNoDialog(project, description, title, yesText, noText, icon);
  }

  @SuppressWarnings("MethodMayBeStatic")
  @Messages.YesNoResult
  protected int doShowYesNoDialog(Project project, String description, String title, String yesText, String noText, @Nullable Icon icon) {
    return Messages.showYesNoDialog(project, description, title, yesText, noText, icon);
  }
}
