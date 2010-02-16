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
import com.intellij.util.containers.ContainerUtil;
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
    private @Nullable TabbedPaneWrapper myTabbedPane;
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
      AbstractBreakpointPanel currentPanel = getSelectedPanel();
      if (currentPanel != null && currentPanel.getHelpID() != null) {
        HelpManager.getInstance().invokeHelp(currentPanel.getHelpID());
      }
      else {
        super.doHelpAction();
      }
    }

    protected JComponent createCenterPanel() {
      for (BreakpointPanelProvider<?> panelProvider : myBreakpointPanelProviders) {
        addPanels(panelProvider);
      }

      JComponent contentComponent = null;
      if (myPanels.size() > 1) {
        final TabbedPaneWrapper tabbedPane = new TabbedPaneWrapper(getDisposable());
        for (AbstractBreakpointPanel breakpointPanel : myPanels) {
          addPanel(breakpointPanel, tabbedPane);
        }

        final ChangeListener tabPaneChangeListener = new ChangeListener() {
          public void stateChanged(ChangeEvent e) {
            AbstractBreakpointPanel panel = getSelectedPanel();
            if (panel != null) {
              panel.ensureSelectionExists();
            }
          }
        };
        tabbedPane.addChangeListener(tabPaneChangeListener);
        myDisposeActions.add(new Runnable() {
          public void run() {
            tabbedPane.removeChangeListener(tabPaneChangeListener);
          }
        });

        myTabbedPane = tabbedPane;
        contentComponent = tabbedPane.getComponent();
      }
      else if (myPanels.size() == 1) {
        contentComponent = myPanels.get(0).getPanel();
      }

      myPanel = new JPanel(new BorderLayout());
      if (contentComponent != null) {
        myPanel.add(contentComponent, BorderLayout.CENTER);
      }

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
      }
    }

    private void addPanel(final AbstractBreakpointPanel panel, final TabbedPaneWrapper tabbedPane) {
      JPanel jPanel = panel.getPanel();
      jPanel.putClientProperty(BREAKPOINT_PANEL, panel);
      tabbedPane.addTab(panel.getTabTitle(), jPanel);
      final int tabIndex = tabbedPane.getTabCount() - 1;
      final AbstractBreakpointPanel.ChangesListener changesListener = new AbstractBreakpointPanel.ChangesListener() {
        public void breakpointsChanged() {
          updateTabTitle(tabbedPane, tabIndex);
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
      if (myTabbedPane != null) {
        JComponent selectedComponent = myTabbedPane.getSelectedComponent();
        return selectedComponent != null ? (AbstractBreakpointPanel)selectedComponent.getClientProperty(BREAKPOINT_PANEL) : null;
      }
      return ContainerUtil.getFirstItem(myPanels, null);
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
        if (myTabbedPane != null) {
          myLastSelectedTabIndex = myTabbedPane.getSelectedIndex();
        }
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
      final TabbedPaneWrapper tabbedPane = myTabbedPane;
      if (tabbedPane != null) {
        for (int idx = 0; idx < tabbedPane.getTabCount(); idx++) {
          updateTabTitle(tabbedPane, idx);
        }
        if (myLastSelectedTabIndex >= tabbedPane.getTabCount() || myLastSelectedTabIndex < 0) {
          myLastSelectedTabIndex = 0;
        }
        tabbedPane.setSelectedIndex(myLastSelectedTabIndex);
      }
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
          final JPanel panel = breakpointPanel.getPanel();
          if (myTabbedPane != null) {
            myTabbedPane.setSelectedComponent(panel);
          }
          breakpointPanel.selectBreakpoint(b);
          return true;
        }
      }
      return false;
    }

    private void updateTabTitle(final TabbedPaneWrapper tabbedPane, final int idx) {
      JComponent component = tabbedPane.getComponentAt(idx);
      for (AbstractBreakpointPanel breakpointPanel : myPanels) {
        if (component == breakpointPanel.getPanel()) {
          Icon icon = breakpointPanel.getTabIcon();
          tabbedPane.setIconAt(idx, icon);
          break;
        }
      }
    }

  }

}
