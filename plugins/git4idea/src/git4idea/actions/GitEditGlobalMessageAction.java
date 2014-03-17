/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.impl.VcsGlobalMessage;
import com.intellij.openapi.vcs.impl.VcsGlobalMessageManager;
import com.intellij.util.SystemProperties;

/**
 * @author Konstantin Bulenkov
 */
public class GitEditGlobalMessageAction extends DumbAwareAction {
  @Override
  public void actionPerformed(AnActionEvent e) {
    final Project project = e.getProject();
    assert project != null;
    final VcsGlobalMessageManager messageManager = VcsGlobalMessageManager.getInstance(project);
    VcsGlobalMessage message = messageManager.getState();

    final String result = Messages.showMultilineInputDialog(project, "<html><body>Enter a broadcast message. The message will appear in the Commit dialog for everyone who works with '" + project.getName() +"' project.<br> To remove the broadcast message just remove its content.</body></html>", "Edit Global Message", message == null ? "" : message.message, null, null);
    if (result != null) {
      if (message == null) {
        message = new VcsGlobalMessage();
        messageManager.loadState(message);
      }
      message.message = result;
      message.author = SystemProperties.getUserName();
      message.when = System.currentTimeMillis();
      ApplicationManager.getApplication().saveAll();
    }

  }

  @Override
  public void update(AnActionEvent e) {
    e.getPresentation().setEnabledAndVisible(ApplicationManager.getApplication().isInternal() && e.getProject() != null);
  }
}
