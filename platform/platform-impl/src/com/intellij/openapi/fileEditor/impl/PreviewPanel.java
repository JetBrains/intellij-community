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
package com.intellij.openapi.fileEditor.impl;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.FileEditorManagerListener;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.impl.ToolWindowImpl;
import com.intellij.openapi.wm.impl.content.ToolWindowContentUi;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentManager;
import com.intellij.ui.content.ContentManagerAdapter;
import com.intellij.ui.content.ContentManagerEvent;
import com.intellij.ui.docking.DockManager;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.EnumSet;

class PreviewPanel extends JPanel {

  private CardLayout myLayout;

  enum ContentType {Files, Usages, Diagrams, Documentation}

  private static final Key<VirtualFile> FILE_KEY = Key.create("v_file");
  private static final int HISTORY_LIMIT = 10;

  private final Project myProject;
  private final FileEditorManagerImpl myManager;
  private final DockManager myDockManager;
  private EditorWindow myWindow;
  private EditorsSplitters myEditorsSplitters;
  private ArrayList<VirtualFile> myHistory = new ArrayList<VirtualFile>();
  private VirtualFile myModifiedFile = null;
  private ToolWindowImpl myToolWindow;
  private VirtualFile myAwaitingForOpen = null;
  private ContentManager myContentManager;
  private Content myStubContent;
  private boolean myBlocked = false;

  private EnumSet<ContentType> myTypes = EnumSet.noneOf(ContentType.class);

  static boolean isAvailable() {
    return UISettings.getInstance().NAVIGATE_TO_PREVIEW;
  }

  PreviewPanel(Project project, FileEditorManagerImpl manager, DockManager dockManager) {
    myProject = project;
    myManager = manager;
    myDockManager = dockManager;
  }

  /*
  * @return null if preview is not avalable
   */
  @Nullable
  EditorWindow getWindow() {
    if (!isAvailable() || isBlocked() || myProject.isDisposed()) return null;
    initToolWindowIfNeed();
    return myWindow;
  }

  boolean isBlocked() {
    return myBlocked;
  }

  private void initToolWindowIfNeed() {
    if (!isAvailable() || ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PREVIEW) != null) return;

    myToolWindow = (ToolWindowImpl)ToolWindowManager.getInstance(myProject)
      .registerToolWindow(ToolWindowId.PREVIEW, this, ToolWindowAnchor.RIGHT, myProject, false);
    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      @Override
      public void uiSettingsChanged(UISettings source) {
        if (!isAvailable()) {
          VirtualFile[] files = myWindow.getFiles();
          for (VirtualFile file : files) {
            close(file);
          }
          ToolWindowManager.getInstance(myProject).unregisterToolWindow(ToolWindowId.PREVIEW);
        }
      }
    }, myProject);
    myToolWindow.setIcon(AllIcons.Actions.PreviewDetails);
    myToolWindow.setContentUiType(ToolWindowContentUiType.COMBO, null);
    myContentManager = myToolWindow.getContentManager();
    myStubContent = myContentManager.getContent(0);
    myContentManager.addContentManagerListener(new ContentManagerAdapter() {
      @Override
      public void selectionChanged(ContentManagerEvent event) {
        final VirtualFile file = event.getContent().getUserData(FILE_KEY);
        if (event.getOperation() == ContentManagerEvent.ContentOperation.remove && file != null && file.equals(myModifiedFile)) {
          close(file);
          return;
        }

        if (event.getOperation() != ContentManagerEvent.ContentOperation.add) return;

        if (file != null) {
          event.getContent().setComponent(PreviewPanel.this);//Actually we share the same component between contents
          if (!file.equals(myWindow.getSelectedFile())) {
            ApplicationManager.getApplication().invokeLater(new Runnable() {
              @Override
              public void run() {
                myManager.openFileWithProviders(file, false, myWindow);
              }
            });
          }
        }
      }
    });

    myEditorsSplitters = new MyEditorsSplitters();

    myProject.getMessageBus().connect().subscribe(FileEditorManagerListener.Before.FILE_EDITOR_MANAGER,
                                                  new FileEditorManagerListener.Before() {
                                                    @Override
                                                    public void beforeFileOpened(@NotNull FileEditorManager source,
                                                                                 @NotNull VirtualFile file) {
                                                      myAwaitingForOpen = file;
                                                      VirtualFile currentFile = getCurrentFile();
                                                      if (currentFile != null &&
                                                          currentFile.equals(myModifiedFile) &&
                                                          !currentFile.equals(file)) {
                                                        close(currentFile);
                                                      }
                                                    }

                                                    @Override
                                                    public void beforeFileClosed(@NotNull FileEditorManager source,
                                                                                 @NotNull VirtualFile file) {
                                                      checkStubContent();
                                                    }
                                                  });
    myEditorsSplitters.createCurrentWindow();
    myWindow = myEditorsSplitters.getCurrentWindow();
    myWindow.setTabsPlacement(UISettings.TABS_NONE);
    myLayout = new CardLayout();
    setLayout(myLayout);
    add(ContentType.Files.toString(), myEditorsSplitters);
    //add(ContentType.Usages.toString(), myUsagesPreview);??? tree or editor ???
    //add(ContentType.Diagrams.toString(), myDiagramPanel);
    //add(ContentType.Documentation.toString(), myDocumentationPanel);//todo
    myToolWindow.setTitleActions(new MoveToEditorTabsAction());
    ArrayList<AnAction> myGearActions = new ArrayList<AnAction>();
    for (ContentType contentType : ContentType.values()) {
      myGearActions.add(new ContentTypeToggleAction(contentType));
    }
    myToolWindow.setAdditionalGearActions(new DefaultActionGroup("Preview", myGearActions));
    myToolWindow.hide(null);
  }

  @Nullable
  private VirtualFile getCurrentFile() {
    VirtualFile[] files = myWindow.getFiles();
    return files.length == 1 ? files[0] : null;
  }

  @NotNull
  private Content addContent(VirtualFile file) {
    myHistory.add(file);
    while (myHistory.size() > HISTORY_LIMIT) {
      myHistory.remove(0);
    }
    String title =
      StringUtil.getShortened(EditorTabbedContainer.calcTabTitle(myProject, file), UISettings.getInstance().EDITOR_TAB_TITLE_LIMIT);

    Content content = myContentManager.getFactory().createContent(this, title, false);
    content.putUserData(ToolWindow.SHOW_CONTENT_ICON, Boolean.TRUE);
    content.putUserData(FILE_KEY, file);
    content.setIcon(file.getFileType().getIcon());
    content.setPopupIcon(file.getFileType().getIcon());

    myContentManager.addContent(content, 0);
    checkStubContent();
    return content;
  }

  private void setSelected(VirtualFile file) {
    Content content = getContent(file);
    if (content == null) {
      content = addContent(file);
    }
    myContentManager.setSelectedContent(content);
    myContentManager.addContent(content, 0);
  }

  @Nullable
  private Content getContent(VirtualFile file) {
    Content[] contents = myContentManager.getContents();
    for (Content content : contents) {
      if (file.equals(content.getUserData(FILE_KEY))) {
        return content;
      }
    }
    return null;
  }

  private void checkStubContent() {
    if (myContentManager.getContents().length == 0) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "false");
      myStubContent.setComponent(this);
      myContentManager.addContent(myStubContent);
      ApplicationManager.getApplication().invokeLater(new Runnable() {
        @Override
        public void run() {
          if (myContentManager.getIndexOfContent(myStubContent) != -1) {
            toggleToolWindow(false);
          }
        }
      });
    }
    else if (myContentManager.getContents().length > 1) {
      myToolWindow.getComponent().putClientProperty(ToolWindowContentUi.HIDE_ID_LABEL, "true");
      myContentManager.removeContent(myStubContent, false);
    }
  }

  private void close(@NotNull VirtualFile file) {
    myHistory.remove(file);
    if (ArrayUtil.find(myEditorsSplitters.getOpenFiles(), file) != -1) {
      myEditorsSplitters.closeFile(file, false);
    }
    if (file.equals(myAwaitingForOpen)) {
      myAwaitingForOpen = null;
    }
    if (file.equals(myModifiedFile)) {
      myBlocked = true;
      try {
        myManager.openFileWithProviders(myModifiedFile, false, true);
      }
      finally {
        myBlocked = false;
      }
      myModifiedFile = null;
    }
    Content content = getContent(file);
    if (content != null) {
      myContentManager.removeContent(content, false);
      checkStubContent();
    }
  }

  private void toggleToolWindow(boolean activate) {
    ToolWindow toolWindow = ToolWindowManager.getInstance(myProject).getToolWindow(ToolWindowId.PREVIEW);
    if (toolWindow != null) {
      if (activate) {
        toolWindow.activate(null, false);
      }
      else {
        toolWindow.hide(null);
      }
    }
  }

  private class MoveToEditorTabsAction extends AnAction {

    public MoveToEditorTabsAction() {
      super("Move to main tabs", "Move to main tabs", AllIcons.Duplicates.SendToTheLeftGrayed);
    }

    @Override
    public void actionPerformed(AnActionEvent e) {
      VirtualFile virtualFile = getCurrentFile();
      if (virtualFile == null) {
        return;
      }

      EditorWindow window = myManager.getCurrentWindow();
      if (window == null) { //main tab set is still not created, rare situation
        myManager.getMainSplitters().createCurrentWindow();
        window = myManager.getCurrentWindow();
      }
      myManager.openFileWithProviders(virtualFile, true, window);
      close(virtualFile);
      toggleToolWindow(false);
    }
  }

  private class ContentTypeToggleAction extends ToggleAction {
    private final ContentType myContentType;

    ContentTypeToggleAction(ContentType contentType) {
      super(contentType.toString());
      myContentType = contentType;
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
      return myTypes.contains(myContentType);
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
      if (state) {
        myTypes.add(myContentType);
      } else {
        myTypes.remove(myContentType);
      }
    }
  }

  private class MyEditorsSplitters extends EditorsSplitters {
    public MyEditorsSplitters() {
      super(myManager, myDockManager, false);
    }

    @Override
    protected void afterFileOpen(VirtualFile file) {
      if (file.equals(myAwaitingForOpen)) {
        setSelected(file);
      }
      myAwaitingForOpen = null;
    }

    @Override
    protected void afterFileClosed(VirtualFile file) {
      close(file);
    }

    @Override
    public void updateFileIcon(@NotNull VirtualFile file) {
      EditorWithProviderComposite composite = myWindow.findFileComposite(file);
      if (composite != null && composite.isModified()) {
        myModifiedFile = file;
      }
    }

    @Override
    protected EditorWindow createEditorWindow() {
      return new EditorWindow(this) {
        @Override
        protected void onBeforeSetEditor(VirtualFile file) {
          VirtualFile currentFile = getCurrentFile();
          if (currentFile != null && currentFile.equals(myModifiedFile)) {
            myBlocked = true;
            try {
              myManager.openFileWithProviders(myModifiedFile, false, true);
            }
            finally {
              myBlocked = false;
            }
          }
          else {
            toggleToolWindow(true);
          }
        }
      };
    }

    @Override
    public void setTabsPlacement(int tabPlacement) {
      super.setTabsPlacement(UISettings.TABS_NONE);
    }

    @Override
    public boolean isPreview() {
      return true;
    }
  }
}
