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
package com.intellij.xdebugger.impl.breakpoints.ui;

import com.intellij.CommonBundle;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.help.HelpManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy;
import com.intellij.ui.TabbedPaneWrapper;
import com.intellij.xdebugger.XDebuggerBundle;
import com.intellij.xdebugger.impl.DebuggerSupport;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.List;

/**
 * created Jun 18, 2001
 * @author Jeka
 */
public class BreakpointsConfigurationDialogFactory {
  private static final @NonNls String BREAKPOINT_PANEL = "breakpoint_panel";
  private final Project myProject;
  private final List<BreakpointPanelProvider> myBreakpointPanelProviders;

  private int myLastSelectedTabIndex = 0;

  public static BreakpointsConfigurationDialogFactory getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, BreakpointsConfigurationDialogFactory.class);
  }

  public BreakpointsConfigurationDialogFactory(Project project) {
    myProject = project;
    DebuggerSupport[] debuggerSupports = DebuggerSupport.getDebuggerSupports();
    myBreakpointPanelProviders = new ArrayList<BreakpointPanelProvider>();
    for (DebuggerSupport debuggerSupport : debuggerSupports) {
      myBreakpointPanelProviders.add(debuggerSupport.getBreakpointPanelProvider());
    }
    Collections.sort(myBreakpointPanelProviders, new Comparator<BreakpointPanelProvider>() {
      public int compare(final BreakpointPanelProvider o1, final BreakpointPanelProvider o2) {
        return o2.getPriority() - o1.getPriority();
      }
    });
  }

  public BreakpointsConfigurationDialog createDialog(@Nullable Object initialBreakpoint) {
    BreakpointsConfigurationDialog dialog = new BreakpointsConfigurationDialog();
    dialog.selectBreakpoint(initialBreakpoint);
    return dialog;
  }

  public class BreakpointsConfigurationDialog extends DialogWrapper {
    private JPanel myPanel;
    private TabbedPaneWrapper myTabbedPane;
    private JComponent myPreferredComponent;
    @Nullable private Runnable myPreparePreferredComponent;
    private final List<Runnable> myDisposeActions = new ArrayList<Runnable>();
    private final List<AbstractBreakpointPanel> myPanels = new ArrayList<AbstractBreakpointPanel>();

    public BreakpointsConfigurationDialog() {
      super(myProject, true);
      setTitle(XDebuggerBundle.message("xbreakpoints.dialog.title"));
      setOKButtonText(CommonBundle.message("button.close"));
      init();
      reset();
    }

    @NonNls
    protected String getDimensionServiceKey() {
      return "#com.intellij.debugger.ui.breakpoints.BreakpointsConfigurationDialog";
    }

    protected Action[] createActions(){
      return new Action[]{getOKAction(), getHelpAction()};
    }

    protected void doHelpAction() {
      final JComponent selectedComponent = myTabbedPane.getSelectedComponent();
      AbstractBreakpointPanel currentPanel = null;
      for (AbstractBreakpointPanel breakpointPanel : myPanels) {
        if (selectedComponent == breakpointPanel.getPanel()) {
          currentPanel = breakpointPanel;
          break;
        }
      }
      if (currentPanel != null && currentPanel.getHelpID() != null) {
        HelpManager.getInstance().invokeHelp(currentPanel.getHelpID());
      }
      else {
        super.doHelpAction();
      }
    }

    protected JComponent createCenterPanel() {
      myTabbedPane = new TabbedPaneWrapper(getDisposable());
      myPanel = new JPanel(new BorderLayout());

      for (BreakpointPanelProvider<?> panelProvider : myBreakpointPanelProviders) {
        addPanels(panelProvider);
      }

      final ChangeListener tabPaneChangeListener = new ChangeListener() {
        public void stateChanged(ChangeEvent e) {
          AbstractBreakpointPanel panel = getSelectedPanel();
          if (panel != null) {
            panel.ensureSelectionExists();
          }
        }
      };
      myTabbedPane.addChangeListener(tabPaneChangeListener);
      myDisposeActions.add(new Runnable() {
        public void run() {
          myTabbedPane.removeChangeListener(tabPaneChangeListener);
        }
      });
      myPanel.add(myTabbedPane.getComponent(), BorderLayout.CENTER);

      // "Enter" and "Esc" keys work like "Close" button.
      ActionListener closeAction = new ActionListener() {
        public void actionPerformed(ActionEvent e) {
          close(CANCEL_EXIT_CODE);
        }
      };
      myPanel.registerKeyboardAction(
        closeAction,
        KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0),
        JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT
      );
      myPanel.setPreferredSize(new Dimension(600, 500));
      return myPanel;
    }

    private <B> void addPanels(final BreakpointPanelProvider<B> panelProvider) {
      Collection<AbstractBreakpointPanel<B>> panels = panelProvider.getBreakpointPanels(myProject, this);
      for (AbstractBreakpointPanel<B> breakpointPanel : panels) {
        myPanels.add(breakpointPanel);
        addPanel(breakpointPanel, breakpointPanel.getTabTitle());
      }
    }

    private void addPanel(final AbstractBreakpointPanel panel, final String title) {
      JPanel jpanel = panel.getPanel();
      jpanel.putClientProperty(BREAKPOINT_PANEL, panel);
      myTabbedPane.addTab(title, jpanel);
      final int tabIndex = myTabbedPane.getTabCount() - 1;
      final AbstractBreakpointPanel.ChangesListener changesListener = new AbstractBreakpointPanel.ChangesListener() {
        public void breakpointsChanged() {
          updateTabTitle(tabIndex);
        }
      };
      panel.addChangesListener(changesListener);
      myDisposeActions.add(new Runnable() {
        public void run() {
          panel.removeChangesListener(changesListener);
        }
      });
    }

    @Nullable
    public AbstractBreakpointPanel getSelectedPanel() {
      JComponent selectedComponent = myTabbedPane.getSelectedComponent();
      return selectedComponent != null ? (AbstractBreakpointPanel)selectedComponent.getClientProperty(BREAKPOINT_PANEL) : null;
    }

    public JComponent getPreferredFocusedComponent() {
      if (myPreferredComponent != null) {
        if (myPreparePreferredComponent != null) {
          myPreparePreferredComponent.run();
        }
        myPreparePreferredComponent = null;
        return myPreferredComponent;
      }
      final TabbedPaneWrapper tabbedPane = myTabbedPane;
      return tabbedPane != null? IdeFocusTraversalPolicy.getPreferredFocusedComponent(tabbedPane.getComponent()) : null;
    }

    public void setPreferredFocusedComponent(final JComponent component, @Nullable Runnable preparePreferredComponent) {
      myPreferredComponent = component;
      myPreparePreferredComponent = preparePreferredComponent;
    }

    public void dispose() {
      apply();
      for (Runnable runnable : myDisposeActions) {
        runnable.run();
      }
      myDisposeActions.clear();
      if (myPanel != null) {
        for (AbstractBreakpointPanel panel : myPanels) {
          panel.dispose();
        }
        myLastSelectedTabIndex = myTabbedPane.getSelectedIndex();
        myPanel.removeAll();
        myPanel = null;
        myTabbedPane = null;
      }
      super.dispose();
    }

    private void apply() {
      for (AbstractBreakpointPanel panel : myPanels) {
        panel.saveBreakpoints();
      }

      for (BreakpointPanelProvider panelProvider : myBreakpointPanelProviders) {
        panelProvider.onDialogClosed(myProject);
      }
    }

    private void reset() {
      for (AbstractBreakpointPanel panel : myPanels) {
        panel.resetBreakpoints();
      }
      updateAllTabTitles();
      if (myLastSelectedTabIndex >= myTabbedPane.getTabCount() || myLastSelectedTabIndex < 0) {
        myLastSelectedTabIndex = 0;
      }
      myTabbedPane.setSelectedIndex(myLastSelectedTabIndex);
    }

    private void selectBreakpoint(@Nullable Object breakpoint) {
      if (breakpoint == null) {
        return;
      }
      for (AbstractBreakpointPanel<?> breakpointPanel : myPanels) {
        if (selectBreakpoint(breakpointPanel, breakpoint)) break;
      }
    }

    private <B> boolean selectBreakpoint(final AbstractBreakpointPanel<B> breakpointPanel, final Object breakpoint) {
      Class<B> aClass = breakpointPanel.getBreakpointClass();
      if (aClass.isInstance(breakpoint)) {
        B b = aClass.cast(breakpoint);
        if (breakpointPanel.canSelectBreakpoint(b)) {
          myTabbedPane.setSelectedComponent(breakpointPanel.getPanel());
          breakpointPanel.selectBreakpoint(b);
          return true;
        }
      }
      return false;
    }

    private void updateAllTabTitles() {
      for (int idx = 0; idx < myTabbedPane.getTabCount(); idx++) {
        updateTabTitle(idx);
      }
    }

    private void updateTabTitle(final int idx) {
      JComponent component = myTabbedPane.getComponentAt(idx);
      for (AbstractBreakpointPanel breakpointPanel : myPanels) {
        if (component == breakpointPanel.getPanel()) {
          Icon icon = breakpointPanel.getTabIcon();
          myTabbedPane.setIconAt(idx, icon);
          break;
        }
      }
    }

  }

}
