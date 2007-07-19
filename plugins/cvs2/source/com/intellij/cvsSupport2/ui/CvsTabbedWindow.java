package com.intellij.cvsSupport2.ui;

import com.intellij.CvsBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowId;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.peer.PeerFactory;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.util.ui.ErrorTreeView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * author: lesya
 */
public class CvsTabbedWindow {
  private Project myProject;
  private Editor myOutput = null;
  private ErrorTreeView myErrorsView;
  private boolean myIsInitialized;
  private boolean myIsDisposed;

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.ui.CvsTabbedWindow");
  private ContentManager myContentManager;

  public CvsTabbedWindow(Project project) {
    myProject = project;
    Disposer.register(project, new Disposable() {
      public void dispose() {
        if (myOutput != null) {
          EditorFactory.getInstance().releaseEditor(myOutput);
          myOutput = null;
        }

        LOG.assertTrue(!myIsDisposed);
        try {
          if (!myIsInitialized) return;
          ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
          toolWindowManager.unregisterToolWindow(ToolWindowId.CVS);
        }
        finally {
          myIsDisposed = true;
        }
      }
    });
  }

  private void initialize() {
    if (myIsInitialized) {
      return;
    }

    myIsInitialized = true;
    myIsDisposed = false;

    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, myProject);
    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      public void contentRemoved(ContentManagerEvent event) {
        JComponent component = event.getContent().getComponent();
        JComponent removedComponent = component instanceof CvsTabbedWindowComponent ?
                                      ((CvsTabbedWindowComponent)component).getComponent() : component;
        if (removedComponent == myErrorsView) {
          myErrorsView.dispose();
          myErrorsView = null;
        }
        else if (myOutput != null && removedComponent == myOutput.getComponent()) {
          EditorFactory.getInstance().releaseEditor(myOutput);
          myOutput = null;
        }
      }
    });

    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
    ToolWindow toolWindow =
      toolWindowManager.registerToolWindow(ToolWindowId.CVS, myContentManager.getComponent(), ToolWindowAnchor.BOTTOM);
    toolWindow.setIcon(IconLoader.getIcon("/_cvs/cvs.png"));
    toolWindow.installWatcher(myContentManager);
  }

  public static CvsTabbedWindow getInstance(Project project) {
    return ServiceManager.getService(project, CvsTabbedWindow.class);
  }

  private int getComponentAt(int i, boolean select) {
    if (select) getContentManager().setSelectedContent(getContentManager().getContent(i));
    return i;
  }

  public int addTab(String s,
                    JComponent component,
                    boolean selectTab,
                    boolean replaceContent,
                    boolean lockable,
                    boolean addDefaultToolbar,
                    @Nullable final ActionGroup toolbarActions,
                    @NonNls String helpId) {

    int existing = getComponentNumNamed(s);
    if (existing != -1) {
      Content existingContent = getContentManager().getContent(existing);
      if (!replaceContent) {
        getContentManager().setSelectedContent(existingContent);
        return existing;
      }
      else if (!existingContent.isPinned()) {
        getContentManager().removeContent(existingContent, true);
        existingContent.release();
      }
    }

    CvsTabbedWindowComponent newComponent = new CvsTabbedWindowComponent(component,
                                                                         addDefaultToolbar, toolbarActions, getContentManager(), helpId);
    Content content = PeerFactory.getInstance().getContentFactory().createContent(newComponent.getShownComponent(), s, lockable);
    newComponent.setContent(content);
    getContentManager().addContent(content);

    return getComponentAt(getContentManager().getContentCount() - 1, selectTab);
  }

  private int getComponentNumNamed(String s) {
    for (int i = 0; i < getContentManager().getContentCount(); i++) {
      if (s.equals(getContentManager().getContent(i).getDisplayName())) {
        return i;
      }
    }
    return -1;
  }

  public Editor addOutput(Editor output) {
    LOG.assertTrue(myOutput == null);
    if (myOutput == null) {
      addTab(CvsBundle.message("tab.title.cvs.output"), output.getComponent(), false, false, false, true, null, "cvs.cvsOutput");
      myOutput = output;
    }
    return myOutput;
  }

  public ErrorTreeView addErrorsTreeView(ErrorTreeView view) {
    if (myErrorsView == null) {
      addTab(CvsBundle.message("tab.title.errors"), view.getComponent(), true, false, true, false, null, "cvs.errors");
      myErrorsView = view;
    }
    return myErrorsView;
  }

  public void hideErrors() {
    //if (myErrorsView == null) return;
    //Content content = getContent(myErrorsView);
    //removeContent(content);
    //myErrorsView = null;
  }

  public void ensureVisible(Project project) {
    if (project == null) return;
    ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(project);
    if (toolWindowManager != null) {
      ToolWindow toolWindow = toolWindowManager.getToolWindow(ToolWindowId.CVS);
      if (toolWindow != null) {
        toolWindow.activate(null);
      }
    }
  }

  public ContentManager getContentManager() {
    initialize();
    return myContentManager;
  }

  public Editor getOutput() {
    return myOutput;
  }
}
