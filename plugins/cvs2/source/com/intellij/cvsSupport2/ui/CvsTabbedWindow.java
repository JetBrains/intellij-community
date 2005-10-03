package com.intellij.cvsSupport2.ui;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.EditorFactory;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
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

import javax.swing.*;

import org.jetbrains.annotations.NonNls;

/**
 * author: lesya
 */
public class CvsTabbedWindow implements ProjectComponent {
  private Project myProject;
  private Editor myOutput = null;
  private ErrorTreeView myErrorsView;
  private boolean myIsInitialized;
  private boolean myIsDisposed;

  private static final Logger LOG = Logger.getInstance("#com.intellij.cvsSupport2.ui.CvsTabbedWindow");
  private final ContentManager myContentManager;

  public CvsTabbedWindow(Project project) {
    myContentManager = PeerFactory.getInstance().getContentFactory().createContentManager(true, project);
    myProject = project;
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
  }

  public static CvsTabbedWindow getInstance(Project project) {
    return project.getComponent(CvsTabbedWindow.class);
  }

  public void initComponent() { }

  public void disposeComponent() {
    if (myOutput != null) {
      EditorFactory.getInstance().releaseEditor(myOutput);
      myOutput = null;
    }
  }

  public void projectClosed() {
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

  public String getComponentName() {
    return "CvsTabbedWindow";
  }

  public void projectOpened() {
    StartupManager.getInstance(myProject).registerPostStartupActivity(new Runnable() {
      public void run() {
        myIsInitialized = true;
        myIsDisposed = false;
        ToolWindowManager toolWindowManager = ToolWindowManager.getInstance(myProject);
        ToolWindow toolWindow = toolWindowManager.registerToolWindow(ToolWindowId.CVS, myContentManager.getComponent(),
                                                                     ToolWindowAnchor.BOTTOM);
        toolWindow.setIcon(IconLoader.getIcon("/_cvs/cvs.png"));
        toolWindow.installWatcher(myContentManager);
      }});
  }

  private int getComponentAt(int i, boolean select) {
    if (select) myContentManager.setSelectedContent(myContentManager.getContent(i));
    return i;
  }

  public int addTab(String s,
                    JComponent component,
                    boolean selectTab,
                    boolean replaceContent,
                    boolean lockable,
                    boolean addDefaultToolbar, @NonNls String helpId) {

    int existing = getComponentNumNamed(s);
    if (existing != -1) {
      Content existingContent = myContentManager.getContent(existing);
      if (!replaceContent) {
        myContentManager.setSelectedContent(existingContent);
        return existing;
      }
      else if (!existingContent.isPinned()) {
        myContentManager.removeContent(existingContent);
        existingContent.release();
      }
    }

    CvsTabbedWindowComponent newComponent = new CvsTabbedWindowComponent(component,
                                                                         addDefaultToolbar, myContentManager, helpId);
    Content content = PeerFactory.getInstance().getContentFactory().createContent(newComponent.getShownComponent(), s, lockable);
    newComponent.setContent(content);
    myContentManager.addContent(content);

    return getComponentAt(myContentManager.getContentCount() - 1, selectTab);
  }

  private int getComponentNumNamed(String s) {
    for (int i = 0; i < myContentManager.getContentCount(); i++) {
      if (s.equals(myContentManager.getContent(i).getDisplayName())) {
        return i;
      }
    }
    return -1;
  }

  public Editor addOutput(Editor output) {
    LOG.assertTrue(myOutput == null);
    if (myOutput == null) {
      addTab(com.intellij.CvsBundle.message("tab.title.cvs.output"), output.getComponent(), false, false, false, true, "cvs.cvsOutput");
      myOutput = output;
    }
    return myOutput;
  }

  public ErrorTreeView addErrorsTreeView(ErrorTreeView view) {
    if (myErrorsView == null) {
      addTab(com.intellij.CvsBundle.message("tab.title.errors"), view.getComponent(), true, false, true, false, "cvs.errors");
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
    return myContentManager;
  }

  public Editor getOutput() {
    return myOutput;
  }
}
