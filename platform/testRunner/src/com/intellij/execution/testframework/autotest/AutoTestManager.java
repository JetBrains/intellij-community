package com.intellij.execution.testframework.autotest;

import com.intellij.execution.DelayedDocumentWatcher;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.ide.util.PropertiesComponent;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.Consumer;
import com.intellij.util.containers.WeakList;

import java.util.Collection;

/**
 * @author yole
 */
public class AutoTestManager {
  static final Key<Boolean> AUTOTESTABLE = Key.create("auto.test.manager.supported");

  private final Project myProject;

  private int myDelay;
  private DelayedDocumentWatcher myDocumentWatcher;
  private final Collection<Content> myEnabledDescriptors = new WeakList<Content>();

  public static AutoTestManager getInstance(Project project) {
    return ServiceManager.getService(project, AutoTestManager.class);
  }

  public AutoTestManager(Project project) {
    myProject = project;
    myDocumentWatcher = createWatcher();
    myDelay = PropertiesComponent.getInstance(myProject).getOrInitInt("auto.test.manager.delay", 3000);
  }

  private DelayedDocumentWatcher createWatcher() {
    return new DelayedDocumentWatcher(myProject, new Alarm(Alarm.ThreadToUse.SWING_THREAD, myProject), myDelay, new Consumer<VirtualFile[]>() {
      @Override
      public void consume(VirtualFile[] files) {
        for (Content content : myEnabledDescriptors) {
          runAutoTest(content);
        }
      }
    }, new Condition<VirtualFile>() {
      @Override
      public boolean value(VirtualFile file) {
        // Vladimir.Krivosheev — I don't know, why AutoTestManager checks it, but old behavior is preserved
        return FileEditorManager.getInstance(myDocumentWatcher.getProject()).isFileOpen(file);
      }
    });
  }

  public void setAutoTestEnabled(RunContentDescriptor descriptor, boolean enabled) {
    Content content = descriptor.getAttachedContent();
    if (enabled) {
      if (!myEnabledDescriptors.contains(content)) {
        myEnabledDescriptors.add(content);
      }
      myDocumentWatcher.activate();
    }
    else {
      myEnabledDescriptors.remove(content);
      if (myEnabledDescriptors.isEmpty()) {
        myDocumentWatcher.deactivate();
      }
    }
  }

  public boolean isAutoTestEnabled(RunContentDescriptor descriptor) {
    return myEnabledDescriptors.contains(descriptor.getAttachedContent());
  }

  private static void runAutoTest(Content content) {
    RunContentDescriptor descriptor = RunContentManagerImpl.getRunContentDescriptorByContent(content);
    if (descriptor == null) {
      return;
    }
    Runnable restarter = descriptor.getRestarter();
    if (restarter == null) {
      return;
    }
    final ProcessHandler processHandler = descriptor.getProcessHandler();
    if (processHandler != null && !processHandler.isProcessTerminated()) {
      return;
    }
    restarter.run();
  }

  int getDelay() {
    return myDelay;
  }

  void setDelay(int delay) {
    myDelay = delay;
    myDocumentWatcher.deactivate();
    myDocumentWatcher = createWatcher();
    if (!myEnabledDescriptors.isEmpty()) {
      myDocumentWatcher.activate();
    }
  }
}