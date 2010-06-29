/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.ide.IdeEventQueue;
import com.intellij.ide.actions.CloseAction;
import com.intellij.ide.actions.ShowFilePathAction;
import com.intellij.ide.ui.UISettings;
import com.intellij.ide.ui.UISettingsListener;
import com.intellij.ide.ui.customization.CustomActionsSchema;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.editor.colors.EditorColorsManager;
import com.intellij.openapi.extensions.Extensions;
import com.intellij.openapi.fileEditor.FileEditor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.ex.FileEditorManagerEx;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Queryable;
import com.intellij.openapi.ui.ShadowAction;
import com.intellij.openapi.util.*;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.*;
import com.intellij.openapi.wm.ex.ToolWindowManagerAdapter;
import com.intellij.openapi.wm.ex.ToolWindowManagerEx;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.ui.switcher.SwitchProvider;
import com.intellij.ui.switcher.SwitchTarget;
import com.intellij.ui.tabs.*;
import com.intellij.ui.tabs.impl.JBTabsImpl;
import com.intellij.util.ui.AwtVisitor;
import com.intellij.util.ui.TimedDeadzone;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * @author Anton Katilin
 * @author Vladimir Kondratyev
 */
final class EditorTabbedContainer implements Disposable, CloseAction.CloseTarget {
  private final EditorWindow myWindow;
  private final Project myProject;
  private final JBTabs myTabs;

  @NonNls public static final String HELP_ID = "ideaInterface.editor";

  EditorTabbedContainer(final EditorWindow window, Project project, int tabPlacement) {
    myWindow = window;
    myProject = project;
    final ActionManager actionManager = ActionManager.getInstance();
    myTabs = new JBTabsImpl(project, actionManager, IdeFocusManager.getInstance(project), this);
    myTabs.setDataProvider(new MyDataProvider()).setPopupGroup(new Getter<ActionGroup>() {
      public ActionGroup get() {
        return (ActionGroup)CustomActionsSchema.getInstance().getCorrectedAction(IdeActions.GROUP_EDITOR_TAB_POPUP);
      }
    }, ActionPlaces.EDITOR_POPUP, false).setNavigationActionsEnabled(false).addTabMouseListener(new TabMouseListener()).getPresentation().
      setTabDraggingEnabled(true).setUiDecorator(new UiDecorator() {
      @NotNull
      public UiDecoration getDecoration() {
        return new UiDecoration(null, new Insets(1, 6, 1, 6));
      }
    }).setTabLabelActionsMouseDeadzone(TimedDeadzone.NULL).setGhostsAlwaysVisible(true).setTabLabelActionsAutoHide(false)
      .setActiveTabFillIn(EditorColorsManager.getInstance().getGlobalScheme().getDefaultBackground()).setPaintFocus(false).getJBTabs()
      .addListener(new TabsListener.Adapter() {
        public void selectionChanged(final TabInfo oldSelection, final TabInfo newSelection) {
          final FileEditorManager editorManager = FileEditorManager.getInstance(myProject);
          final FileEditor oldEditor = oldSelection != null ? editorManager.getSelectedEditor((VirtualFile)oldSelection.getObject()) : null;
          if (oldEditor != null) {
            oldEditor.deselectNotify();
          }

          final FileEditor newEditor = editorManager.getSelectedEditor((VirtualFile)newSelection.getObject());
          if (newEditor != null) {
            newEditor.selectNotify();
          }
        }
      }).setAdditinalSwitchProviderWhenOriginal(new MySwitchProvider());

    setTabPlacement(UISettings.getInstance().EDITOR_TAB_PLACEMENT);

    updateTabBorder();

    ((ToolWindowManagerEx)ToolWindowManager.getInstance(myProject)).addToolWindowManagerListener(new ToolWindowManagerAdapter() {
      public void stateChanged() {
        updateTabBorder();
      }

      public void toolWindowRegistered(@NotNull final String id) {
        updateTabBorder();
      }
    });

    UISettings.getInstance().addUISettingsListener(new UISettingsListener() {
      public void uiSettingsChanged(UISettings source) {
        updateTabBorder();
      }
    });

    Disposer.register(project, this);
  }

  public int getTabCount() {
    return myTabs.getTabCount();
  }

  public ActionCallback setSelectedIndex(final int indexToSelect) {
    return setSelectedIndex(indexToSelect, true);
  }

  public ActionCallback setSelectedIndex(final int indexToSelect, boolean focusEditor) {
    return myTabs.select(myTabs.getTabAt(indexToSelect), focusEditor);
  }

  private void updateTabBorder() {
    if (!myProject.isOpen()) return;

    ToolWindowManagerEx mgr = (ToolWindowManagerEx)ToolWindowManager.getInstance(myProject);

    String[] ids = mgr.getToolWindowIds();

    Insets border = new Insets(0, 0, 0, 0);

    UISettings uiSettings = UISettings.getInstance();

    List<String> topIds = mgr.getIdsOn(ToolWindowAnchor.TOP);
    List<String> bottom = mgr.getIdsOn(ToolWindowAnchor.BOTTOM);
    List<String> rightIds = mgr.getIdsOn(ToolWindowAnchor.RIGHT);
    List<String> leftIds = mgr.getIdsOn(ToolWindowAnchor.LEFT);

    if (!uiSettings.HIDE_TOOL_STRIPES) {
      border.top = topIds.size() > 0 ? 1: 0;
      border.bottom = bottom.size() > 0 ? 1: 0;
      border.left = leftIds.size() > 0 ? 1: 0;
      border.right = rightIds.size() > 0 ? 1: 0;
    }

    for (String each : ids) {
      ToolWindow eachWnd = mgr.getToolWindow(each);
      if (!eachWnd.isAvailable()) continue;

      if (eachWnd.isVisible() && eachWnd.getType() == ToolWindowType.DOCKED) {
        ToolWindowAnchor eachAnchor = eachWnd.getAnchor();
        if (eachAnchor == ToolWindowAnchor.TOP) {
          border.top = 1;
        } else if (eachAnchor == ToolWindowAnchor.BOTTOM) {
          border.bottom = 1;
        } else if (eachAnchor == ToolWindowAnchor.LEFT) {
          border.left = 1;
        } else if (eachAnchor == ToolWindowAnchor.RIGHT) {
          border.right = 0;
        }
      }
    }

    myTabs.getPresentation().setPaintBorder(border.top, border.left, border.right, border.bottom).setTabSidePaintBorder(5);
  }

  public Component getComponent() {
    return myTabs.getComponent();
  }

  public ActionCallback removeTabAt(final int componentIndex, int indexToSelect, boolean transferFocus) {
    TabInfo toSelect = indexToSelect >= 0 && indexToSelect < myTabs.getTabCount() ? myTabs.getTabAt(indexToSelect) : null;
    final ActionCallback callback = myTabs.removeTab(myTabs.getTabAt(componentIndex), toSelect, transferFocus);
    return myProject.isOpen() ? callback : new ActionCallback.Done();
  }

  public ActionCallback removeTabAt(final int componentIndex, int indexToSelect) {
    return removeTabAt(componentIndex, indexToSelect, true);
  }

  public int getSelectedIndex() {
    return myTabs.getIndexOf(myTabs.getSelectedInfo());
  }

  public void setForegroundAt(final int index, final Color color) {
    myTabs.getTabAt(index).setDefaultForeground(color);
  }

  public void setWaveColor(final int index, @Nullable final Color color) {
    final TabInfo tab = myTabs.getTabAt(index);
    tab.setDefaultStyle(color == null ? SimpleTextAttributes.STYLE_PLAIN : SimpleTextAttributes.STYLE_WAVED);
    tab.setDefaultWaveColor(color);
  }

  public void setIconAt(final int index, final Icon icon) {
    myTabs.getTabAt(index).setIcon(icon);
  }

  public void setTitleAt(final int index, final String text) {
    myTabs.getTabAt(index).setText(text);
  }

  public void setToolTipTextAt(final int index, final String text) {
    myTabs.getTabAt(index).setTooltipText(text);
  }

  public void setBackgroundColorAt(final int index, final Color color) {
    myTabs.getTabAt(index).setTabColor(color);
  }

  public void setTabLayoutPolicy(final int policy) {
    switch (policy) {
      case JTabbedPane.SCROLL_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(true);
        break;
      case JTabbedPane.WRAP_TAB_LAYOUT:
        myTabs.getPresentation().setSingleRow(false);
        break;
      default:
        throw new IllegalArgumentException("Unsupported tab layout policy: " + policy);
    }
  }

  public void setTabPlacement(final int tabPlacement) {
    switch (tabPlacement) {
      case SwingConstants.TOP:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.top);
        break;
      case SwingConstants.BOTTOM:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.bottom);
        break;
      case SwingConstants.LEFT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.left);
        break;
      case SwingConstants.RIGHT:
        myTabs.getPresentation().setTabsPosition(JBTabsPosition.right);
        break;
      default:
        throw new IllegalArgumentException("Unknown tab placement code=" + tabPlacement);
    }
  }

  @Nullable
  public Object getSelectedComponent() {
    final TabInfo info = myTabs.getTargetInfo();
    return info != null ? info.getComponent() : null;
  }

  public void insertTab(final VirtualFile file, final Icon icon, final JComponent comp, final String tooltip, final int indexToInsert) {

    TabInfo tab = myTabs.findInfo(file);
    if (tab != null) return;

    tab = new TabInfo(comp).setText(calcTabTitle(myProject, file)).setIcon(icon).setTooltipText(tooltip).setObject(file)
      .setTabColor(calcTabColor(myProject, file));
    tab.setTestableUi(new MyQueryable(tab));

    final DefaultActionGroup tabActions = new DefaultActionGroup();
    tabActions.add(new CloseTab(comp, tab));

    tab.setTabLabelActions(tabActions, ActionPlaces.EDITOR_TAB);
    myTabs.addTab(tab, indexToInsert);
  }

  private class MyQueryable implements Queryable {

    private final TabInfo myTab;

    public MyQueryable(TabInfo tab) {
      myTab = tab;
    }

    public void putInfo(Map<String, String> info) {
      info.put("editorTab", myTab.getText());
    }
  }

  public static String calcTabTitle(final Project project, final VirtualFile file) {
    for (EditorTabTitleProvider provider : Extensions.getExtensions(EditorTabTitleProvider.EP_NAME)) {
      final String result = provider.getEditorTabTitle(project, file);
      if (result != null) {
        return result;
      }
    }

    return file.getPresentableName();
  }

  public static Color calcTabColor(final Project project, final VirtualFile file) {
    for (EditorTabColorProvider provider : Extensions.getExtensions(EditorTabColorProvider.EP_NAME)) {
      final Color result = provider.getEditorTabColor(project, file);
      if (result != null) {
        return result;
      }
    }

    return null;
  }

  public Component getComponentAt(final int i) {
    final TabInfo tab = myTabs.getTabAt(i);
    return tab.getComponent();
  }

  public void dispose() {

  }

  private class CloseTab extends AnAction implements DumbAware {

    ShadowAction myShadow;
    private final TabInfo myTabInfo;
    private final Icon myIcon = IconLoader.getIcon("/actions/close.png");
    private final Icon myHoveredIcon = IconLoader.getIcon("/actions/closeHovered.png");

    public CloseTab(JComponent c, TabInfo info) {
      myTabInfo = info;
      myShadow = new ShadowAction(this, ActionManager.getInstance().getAction(IdeActions.ACTION_CLOSE), c);
    }

    @Override
    public void update(final AnActionEvent e) {
      e.getPresentation().setIcon(myIcon);
      e.getPresentation().setHoveredIcon(myHoveredIcon);
      e.getPresentation().setVisible(UISettings.getInstance().SHOW_CLOSE_BUTTON);
    }

    public void actionPerformed(final AnActionEvent e) {
      final FileEditorManagerEx mgr = FileEditorManagerEx.getInstanceEx(myProject);
      EditorWindow window;
      final VirtualFile file = (VirtualFile)myTabInfo.getObject();
      if (ActionPlaces.EDITOR_TAB.equals(e.getPlace())) {
        window = myWindow;
      }
      else {
        window = mgr.getCurrentWindow();
      }

      if (window != null) {
        if (window.findFileComposite(file) != null) {
          mgr.closeFile(file, window);
        }
      }
    }
  }

  private class MyDataProvider implements DataProvider {
    public Object getData(@NonNls final String dataId) {
      if (PlatformDataKeys.PROJECT.is(dataId)) {
        return myProject;
      }
      if (PlatformDataKeys.VIRTUAL_FILE.is(dataId)) {
        final VirtualFile selectedFile = myWindow.getSelectedFile();
        return selectedFile != null && selectedFile.isValid() ? selectedFile : null;
      }
      if (EditorWindow.DATA_KEY.is(dataId)) {
        return myWindow;
      }
      if (PlatformDataKeys.HELP_ID.is(dataId)) {
        return HELP_ID;
      }

      if (CloseAction.CloseTarget.KEY.is(dataId)) {
        TabInfo selected = myTabs.getSelectedInfo();
        if (selected != null) {
          return EditorTabbedContainer.this;
        }
      }

      return null;
    }
  }

  public void close() {
    TabInfo selected = myTabs.getSelectedInfo();
    if (selected == null) return;

    VirtualFile file = (VirtualFile)selected.getObject();
    final FileEditorManagerEx mgr = FileEditorManagerEx.getInstanceEx(myProject);
    EditorWindow wnd = mgr.getCurrentWindow();
    if (wnd != null) {
      if (wnd.findFileComposite(file) != null) {
        mgr.closeFile(file, wnd);
      }
    }
  }

  private class TabMouseListener extends MouseAdapter {
    @Override
    public void mousePressed(final MouseEvent e) {
      if (UIUtil.isCloseClick(e, MouseEvent.MOUSE_PRESSED)) {
        final TabInfo info = myTabs.findInfo(e);
        if (info != null) {
          IdeEventQueue.getInstance().blockNextEvents(e);
          myWindow.closeFile((VirtualFile)info.getObject());
          return;
        }
      }

      if (UIUtil.isActionClick(e) && (e.getClickCount() % 2) == 0) {
        final ActionManager mgr = ActionManager.getInstance();
        mgr.tryToExecute(mgr.getAction("HideAllWindows"), e, null, ActionPlaces.UNKNOWN, true);
      }
      else if (UIUtil.isActionClick(e) && (e.isMetaDown() || (!SystemInfo.isMac && e.isControlDown()))) {
        final TabInfo info = myTabs.findInfo(e);
        if (info != null && info.getObject() != null) {
          final VirtualFile vFile = (VirtualFile)info.getObject();
          ShowFilePathAction.show(vFile, e);
        }
      }
    }
  }

  private class MySwitchProvider implements SwitchProvider {
    public List<SwitchTarget> getTargets(final boolean onlyVisible, boolean originalProvider) {
      final ArrayList<SwitchTarget> result = new ArrayList<SwitchTarget>();
      TabInfo selected = myTabs.getSelectedInfo();
      new AwtVisitor(selected.getComponent()) {
        @Override
        public boolean visit(Component component) {
          if (component instanceof JBTabs) {
            JBTabs tabs = (JBTabs)component;
            if (tabs != myTabs) {
              result.addAll(tabs.getTargets(onlyVisible, false));
              return true;
            }
          }
          return false;
        }
      };
      return result;
    }

    public SwitchTarget getCurrentTarget() {
      TabInfo selected = myTabs.getSelectedInfo();
      final Ref<SwitchTarget> targetRef = new Ref<SwitchTarget>();
      new AwtVisitor(selected.getComponent()) {
        @Override
        public boolean visit(Component component) {
          if (component instanceof JBTabs) {
            JBTabs tabs = (JBTabs)component;
            if (tabs != myTabs) {
              targetRef.set(tabs.getCurrentTarget());
              return true;
            }
          }
          return false;
        }
      };

      return targetRef.get();
    }

    public JComponent getComponent() {
      return null;
    }

    public boolean isCycleRoot() {
      return false;
    }
  }
}
