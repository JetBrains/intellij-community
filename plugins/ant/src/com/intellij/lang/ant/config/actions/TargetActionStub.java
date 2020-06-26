// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.lang.ant.config.actions;

import com.intellij.lang.ant.AntDisposable;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.lang.ant.config.AntConfigurationListener;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import org.jetbrains.annotations.NotNull;

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
    Disposer.register(AntDisposable.getInstance(project), this);
  }

  @Override
  public void dispose() {
    ActionManager.getInstance().unregisterAction(myActionId);
    myProject = null;
  }

  @Override
  public void actionPerformed(@NotNull final AnActionEvent e) {
    if (myProject == null) {
      return;
    }
    try {
      final AntConfiguration config = AntConfiguration.getInstance(myProject);  // this call will also lead to ant configuration loading
      final AntConfigurationListener listener = new AntConfigurationListener() {
        @Override
        public void configurationLoaded() {
          config.removeAntConfigurationListener(this);
          invokeAction(e);
        }
      };
      config.addAntConfigurationListener(listener);
      Disposer.register(AntDisposable.getInstance(myProject), new ListenerRemover(config, listener));
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

  private static final class ListenerRemover implements Disposable {
    private AntConfiguration myConfig;
    private AntConfigurationListener myListener;

    private ListenerRemover(AntConfiguration config, AntConfigurationListener listener) {
      myConfig = config;
      myListener = listener;
    }

    @Override
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
