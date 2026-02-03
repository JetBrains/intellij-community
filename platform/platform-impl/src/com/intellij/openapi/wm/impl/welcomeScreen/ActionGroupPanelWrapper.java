// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionWrapper;
import com.intellij.openapi.actionSystem.CommonShortcuts;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedActionGroup;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsibleGroupedItemsListRenderer;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsibleGroupedListSelectionModel;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.ListListenerCollapsedActionGroupExpander;
import com.intellij.ui.Gray;
import com.intellij.ui.JBColor;
import com.intellij.ui.ListSpeedSearch;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.ScrollingUtil;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.BorderFactory;
import javax.swing.DefaultListModel;
import javax.swing.DefaultListSelectionModel;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.SwingUtilities;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getProjectsBackground;

/** Helper methods for creating action group panels in the "welcome screen" and "new project dialog". */
public final class ActionGroupPanelWrapper {
  /** Only for AbstractNewProjectDialog */
  @ApiStatus.Obsolete
  public static Pair<JPanel, JBList<AnAction>> createActionGroupPanel(@NotNull ActionGroup actionGroup,
                                                                      @Nullable Runnable backAction,
                                                                      @NotNull Disposable parentDisposable) {
    var items = getFlattenedActionGroups(actionGroup);
    if (!items.isEmpty()) {
      items.removeFirst(); // Skip the root group.
    }
    return createActionGroupPanel(items, backAction, parentDisposable);
  }

  public static Pair<JPanel, JBList<AnAction>> createActionGroupPanel(@NotNull List<AnAction> actions,
                                                                      @Nullable Runnable backAction,
                                                                      @NotNull Disposable parentDisposable) {
    DefaultListModel<AnAction> model = JBList.createDefaultListModel(actions);
    JBList<AnAction> list = new JBList<>(model);
    list.setSelectionModel(new CollapsibleGroupedListSelectionModel(model));
    list.setCellRenderer(new CollapsibleGroupedItemsListRenderer());
    list.setBackground(getProjectsBackground());

    ListListenerCollapsedActionGroupExpander.expandCollapsableGroupsOnClick(list, model);
    for (AnAction action : actions) {
      if (action instanceof Disposable) {
        Disposer.register(parentDisposable, (Disposable)action);
      }
    }

    JScrollPane pane = ScrollPaneFactory.createScrollPane(list, true);
    pane.setBackground(getProjectsBackground());

    JPanel actionsListPanel = new JPanel(new BorderLayout());
    actionsListPanel.setBackground(getProjectsBackground());
    actionsListPanel.add(pane, BorderLayout.CENTER);
    actionsListPanel.setBorder(JBUI.Borders.customLineRight(JBColor.border()));

    int width = Math.clamp(Math.round(list.getPreferredSize().getWidth()), JBUIScale.scale(100), JBUIScale.scale(200));
    pane.setPreferredSize(JBUI.size(width + 14, -1));

    boolean singleProjectGenerator = list.getModel().getSize() == 1;

    final Ref<Component> selected = Ref.create();
    final HashMap<Object, JPanel> panelsMap = new HashMap<>();
    final Set<JButton> actionButtonsCache = new HashSet<>();
    final JPanel main = new NonOpaquePanel(new BorderLayout()) {
      @Override
      public void updateUI() {
        super.updateUI();

        // Update all UI components that are detached from windows
        if (SwingUtilities.getWindowAncestor(this) == null) {
          for (Component component : getComponents()) {
            IJSwingUtilities.updateComponentTreeUI(component);
          }
        }
        for (JPanel panel : panelsMap.values()) {
          if (panel.getParent() == null) {
            IJSwingUtilities.updateComponentTreeUI(panel);
          }
        }
        for (JButton button : actionButtonsCache) {
          if (button.getParent() == null) {
            IJSwingUtilities.updateComponentTreeUI(button);
          }
        }
      }
    };
    main.setBorder(JBUI.Borders.customLineTop(JBColor.border()));
    main.add(actionsListPanel, BorderLayout.WEST);

    JPanel bottomPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT));
    bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new JBColor(Gray._217, Gray._81)));
    main.add(bottomPanel, BorderLayout.SOUTH);

    ListSelectionListener selectionListener = e -> {
      if (e.getValueIsAdjusting()) {
        // Update when a change has been finalized.
        // For instance, selecting an element with mouse fires two consecutive ListSelectionEvent events.
        return;
      }
      if (!selected.isNull()) {
        main.remove(selected.get());
      }
      Object value = list.getSelectedValue();
      if (value instanceof AbstractActionWithPanel) {
        final JPanel panel = panelsMap.computeIfAbsent(value, o -> ((AbstractActionWithPanel)value).createPanel());
        ((AbstractActionWithPanel)value).onPanelSelected();

        panel.setBorder(JBUI.Borders.empty(7, 10));
        selected.set(panel);
        main.add(selected.get());

        JButton actionButton = ((AbstractActionWithPanel)value).getActionButton();
        actionButtonsCache.add(actionButton);
        updateBottomPanel(panel, actionButton, bottomPanel, backAction);

        main.revalidate();
        main.repaint();
      }
    };
    list.addListSelectionListener(selectionListener);
    if (backAction != null) {
      new DumbAwareAction() {
        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(!StackingPopupDispatcher.getInstance().isPopupFocused());
        }

        @Override
        public @NotNull ActionUpdateThread getActionUpdateThread() {
          return ActionUpdateThread.EDT;
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          backAction.run();
        }
      }.registerCustomShortcutSet(CommonShortcuts.ESCAPE, main, parentDisposable);
    }
    installQuickSearch(list);

    if (singleProjectGenerator) {
      actionsListPanel.setPreferredSize(new Dimension(0, 0));
    }

    return Pair.create(main, list);
  }

  private static void updateBottomPanel(@NotNull JPanel currentPanel,
                                        @NotNull JButton actionButton,
                                        @NotNull JPanel bottomPanel,
                                        @Nullable Runnable backAction) {
    bottomPanel.removeAll();

    if (SystemInfo.isMac) {
      addCancelButton(bottomPanel, backAction);
      addActionButton(bottomPanel, actionButton, currentPanel);
    }
    else {
      addActionButton(bottomPanel, actionButton, currentPanel);
      addCancelButton(bottomPanel, backAction);
    }
  }

  private static void addCancelButton(@NotNull JPanel bottomPanel, @Nullable Runnable backAction) {
    JComponent cancelButton = createCancelButton(backAction);
    if (cancelButton != null) {
      bottomPanel.add(cancelButton);
    }
  }

  private static void addActionButton(@NotNull JPanel bottomPanel,
                                      @NotNull JButton actionButton,
                                      @NotNull JPanel currentPanel) {
    bottomPanel.add(actionButton);
    currentPanel.getRootPane().setDefaultButton(actionButton);
  }

  private static @Nullable JComponent createCancelButton(@Nullable Runnable cancelAction) {
    if (cancelAction == null) return null;

    JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
    cancelButton.addActionListener(e -> cancelAction.run());

    return cancelButton;
  }

  public static void installQuickSearch(JBList<? extends AnAction> list) {
    ListSpeedSearch.installOn(list, o -> {
      if (o instanceof AbstractActionWithPanel) { //to avoid dependency mess with ProjectSettingsStepBase
        return o.getTemplatePresentation().getText();
      }
      return null;
    });
  }

  private static ArrayList<AnAction> getFlattenedActionGroups(ActionGroup actionGroup) {
    ArrayList<AnAction> flatActions = new ArrayList<>();

    AnAction[] children;
    if (actionGroup instanceof DefaultActionGroup) {
      children = ((DefaultActionGroup)actionGroup).getChildren(ActionManager.getInstance());
    }
    else {
      children = actionGroup.getChildren(null);
    }

    if (children.length != 0) {
      flatActions.add(actionGroup);
    }

    if (actionGroup instanceof CollapsedActionGroup && ((CollapsedActionGroup)actionGroup).getCollapsed()) {
      return flatActions;
    }

    for (AnAction child : children) {
      if (child instanceof ActionGroup g) {
        flatActions.addAll(getFlattenedActionGroups(g));
      }
      else {
        flatActions.add(child);
      }
    }
    return flatActions;
  }

  private static @NotNull List<AnAction> getFlattenedActionGroups(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent event) {
    ArrayList<AnAction> flatActions = new ArrayList<>();

    List<? extends AnAction> children = event.getUpdateSession().children(actionGroup);
    if (!children.isEmpty()) {
      flatActions.add(actionGroup);
    }

    if (actionGroup instanceof CollapsedActionGroup && ((CollapsedActionGroup)actionGroup).getCollapsed()) {
      return flatActions;
    }

    for (AnAction action : children) {
      if (action instanceof ActionGroup g) {
        flatActions.addAll(getFlattenedActionGroups(g, event));
      }
      else {
        flatActions.add(action);
      }
    }
    return flatActions;
  }

  public static @NotNull AnAction wrapGroups(@NotNull ActionGroup action, @NotNull Disposable parentDisposable) {
    if (!(action instanceof ActionsWithPanelProvider)) return action;

    return new AnActionWrapper(action) {
      volatile Component actionPanel;
      volatile Runnable onDone;

      @Override
      public void update(@NotNull AnActionEvent e) {
        super.update(e);
        if (actionPanel == null) {
          var flatChildrenAndGroups = getFlattenedActionGroups(action, e);
          if (!flatChildrenAndGroups.isEmpty()) {
            flatChildrenAndGroups.removeFirst(); // Skip the root group.
          }
          e.getUpdateSession().compute(this, "initPanel", ActionUpdateThread.EDT, () -> {
            initPanel(e, flatChildrenAndGroups);
            return true;
          });
        }
      }

      @Override
      public void actionPerformed(@NotNull AnActionEvent e) {
        ApplicationManager.getApplication().getMessageBus().syncPublisher(WelcomeScreenComponentListener.COMPONENT_CHANGED)
          .attachComponent(actionPanel, onDone);
      }

      private void initPanel(@NotNull AnActionEvent e, @NotNull List<AnAction> flatChildren) {
        String text = e.getPresentation().getText();
        Pair<JPanel, JBList<AnAction>> panel =
          createActionGroupPanel(flatChildren, () -> goBack(actionPanel), parentDisposable);
        panel.first.setName(action.getClass().getName());
        actionPanel = panel.first;
        onDone = () -> {
          if (text != null) setTitle(StringUtil.removeEllipsisSuffix(text));
          JBList<AnAction> list = panel.second;
          ScrollingUtil.ensureSelectionExists(list);
          ListSelectionListener[] listeners =
            ((DefaultListSelectionModel)list.getSelectionModel()).getListeners(ListSelectionListener.class);

          //avoid component cashing. This helps in case of LaF change
          for (ListSelectionListener listener : listeners) {
            listener.valueChanged(new ListSelectionEvent(list, list.getSelectedIndex(), list.getSelectedIndex(), false));
          }
          JComponent toFocus = FlatWelcomeFrame.getPreferredFocusedComponent(panel);
          IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(
            () -> IdeFocusManager.getGlobalInstance().requestFocus(toFocus, true));
        };
      }
    };
  }

  private static void goBack(@Nullable Component parentComponent) {
    if (parentComponent == null) return;
    ApplicationManager.getApplication().getMessageBus().syncPublisher(WelcomeScreenComponentListener.COMPONENT_CHANGED)
      .detachComponent(parentComponent, null);
  }

  static void setTitle(@Nullable @NlsContexts.DialogTitle String title) {
    JFrame frame = WindowManager.getInstance().findVisibleFrame();
    if (frame != null) {
      frame.setTitle(title);
    }
  }
}