/*
 * Copyright 2000-2016 JetBrains s.r.o.
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

package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author Eugene Zhuravlev
 */
public class TargetActionStub extends AnAction implements Disposable {
  private final String myActionId;
  private Project myProject;
  private final AtomicBoolean myActionInvoked = new AtomicBoolean(false);

  public TargetActionStub(String actionId, Project project) {
    super("ant target action stub");
    myActionId = actionId;
    myProject = project;
    Disposer.register(project, this);
  }

  public void dispose() {
    ActionManager.getInstance().unregisterAction(myActionId);
    myProject = null;
  }

  public void actionPerformed(final AnActionEvent e) {
    if (myProject == null) {
      return;
    }
    try {
      final AntConfiguration config = AntConfiguration.getInstance(myProject);  // this call will also lead to ant configuration loading
      final AntConfigurationListener listener = new AntConfigurationListener() {
        public void configurationLoaded() {
          config.removeAntConfigurationListener(this);
          invokeAction(e);
        }
      };
      config.addAntConfigurationListener(listener);
      Disposer.register(myProject, new ListenerRemover(config, listener));
    }
    finally {
      invokeAction(e);
      Disposer.dispose(this);
    }
  }
  
  private void invokeAction(final AnActionEvent e) {
    final AnAction action = ActionManager.getInstance().getAction(myActionId);
    if (action == null || action instanceof TargetActionStub) {
      return;
    }
    if (!myActionInvoked.getAndSet(true)) {
      //final DataContext context = e.getDataContext();
      //if (context instanceof DataManagerImpl.MyDataContext) {
      //  // hack: macro.expand() can cause UI events such as showing dialogs ('Prompt' macro) which may 'invalidate' the datacontext
      //  // since we know exactly that context is valid, we need to update its event count
      //  ((DataManagerImpl.MyDataContext)context).setEventCount(IdeEventQueue.getInstance().getEventCount());
      //}
      action.actionPerformed(e);
    }
  }
  
  private static class ListenerRemover implements Disposable {
    private AntConfiguration myConfig;
    private AntConfigurationListener myListener;

    private ListenerRemover(AntConfiguration config, AntConfigurationListener listener) {
      myConfig = config;
      myListener = listener;
    }

    public void dispose() {
      final AntConfiguration configuration = myConfig;
      final AntConfigurationListener listener = myListener;
      myConfig = null;
      myListener = null;
      if (configuration != null && listener != null) {
        configuration.removeAntConfigurationListener(listener);
      }
    }
  }
}
