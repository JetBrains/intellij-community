package com.intellij.execution.testframework.autotest;

import com.intellij.execution.process.ProcessHandler;
import com.intellij.execution.ui.RunContentDescriptor;
import com.intellij.execution.ui.RunContentManagerImpl;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.editor.event.DocumentAdapter;
import com.intellij.openapi.editor.event.DocumentEvent;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.content.Content;
import com.intellij.util.Alarm;
import com.intellij.util.containers.WeakList;

import java.util.Collection;

/**
 * @author yole
 */
public class AutoTestManager {
  private final Project myProject;
  private final Alarm myAutoTestAlarm;

  private static final int AUTOTEST_DELAY = 2000;
  private final Runnable myRunTestsRunnable;
  private boolean myListenerAttached;
  private final MyDocumentAdapter myListener;

  public static AutoTestManager getInstance(Project project) {
    return ServiceManager.getService(project, AutoTestManager.class);
  }

  private final Collection<Content> myEnabledDescriptors = new WeakList<Content>();

  public AutoTestManager(Project project) {
    myProject = project;
    myAutoTestAlarm = new Alarm(Alarm.ThreadToUse.SWING_THREAD, project);
    myRunTestsRunnable = new Runnable() {
      public void run() {
        runAutoTests();
      }
    };
    myListener = new MyDocumentAdapter();
  }

  public void setAutoTestEnabled(RunContentDescriptor descriptor, boolean enabled) {
    Content content = descriptor.getAttachedContent();
    if (enabled) {
      if (!myEnabledDescriptors.contains(content)) {
        myEnabledDescriptors.add(content);
      }
      if (!myListenerAttached) {
        myListenerAttached = true;
        EditorFactory.getInstance().getEventMulticaster().addDocumentListener(myListener, myProject);
      }
    }
    else {
      myEnabledDescriptors.remove(content);
      if (myEnabledDescriptors.isEmpty() && myListenerAttached) {
        myListenerAttached = false;
        EditorFactory.getInstance().getEventMulticaster().removeDocumentListener(myListener);
      }
    }
  }

  public boolean isAutoTestEnabled(RunContentDescriptor descriptor) {
    return myEnabledDescriptors.contains(descriptor.getAttachedContent());
  }

  public void runAutoTests() {
    for (Content content : myEnabledDescriptors) {
      runAutoTest(content);
    }
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

  private class MyDocumentAdapter extends DocumentAdapter {
    public void documentChanged(DocumentEvent event) {
      final Document document = event.getDocument();
      final VirtualFile vFile = FileDocumentManager.getInstance().getFile(document);
      if (vFile != null) {
        final FileEditor[] editors = FileEditorManager.getInstance(myProject).getEditors(vFile);
        if (editors.length > 0) {
          myAutoTestAlarm.cancelAllRequests();
          myAutoTestAlarm.addRequest(myRunTestsRunnable, AUTOTEST_DELAY);
        }
      }
    }
  }
}