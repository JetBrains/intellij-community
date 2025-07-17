// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.impl.PresentationFactory;
import com.intellij.openapi.actionSystem.impl.Utils;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedActionGroup;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.CollapsedButtonKt;
import com.intellij.openapi.wm.impl.welcomeScreen.collapsedActionGroup.ListListenerCollapsedActionGroupExpander;
import com.intellij.ui.*;
import com.intellij.ui.SingleSelectionModel;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.IJSwingUtilities;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.*;
import java.util.List;

import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getProjectsBackground;

public final class ActionGroupPanelWrapper {

  private static final Key<@NlsContexts.Separator String> GROUP_NAME = Key.create("ACTION_GROUP_KEY");

  /** Only for AbstractNewProjectDialog */
  @ApiStatus.Obsolete
  public static Pair<JPanel, JBList<AnAction>> createActionGroupPanel(@NotNull ActionGroup actionGroup,
                                                                      @Nullable Runnable backAction,
                                                                      @NotNull Disposable parentDisposable) {
    var presentationFactory = new PresentationFactory();
    var dataContext = DataContext.EMPTY_CONTEXT;
    class Wrapper extends ActionGroupWrapper {
      Wrapper(@NotNull ActionGroup action) {
        super(action);
      }

      @Override
      public AnAction @NotNull [] getChildren(@Nullable AnActionEvent e) {
        if (e == null) return EMPTY_ARRAY;
        AnAction[] children = super.getChildren(e);
        String groupName = e.getPresentation().getText();
        for (int i = 0; i < children.length; i++) {
          if (children[i] instanceof ActionGroup g &&
              !e.getUpdateSession().presentation(g).isPopupGroup()) children[i] = new Wrapper(g);
          else if (groupName != null) setParentGroupName(groupName, children[i]);
        }
        return children;
      }
    }
    List<AnAction> flatChildren = Utils.expandActionGroup(
      new Wrapper(actionGroup), presentationFactory, dataContext, ActionPlaces.NEW_PROJECT_WIZARD, ActionUiKind.NONE);
    return createActionGroupPanel(flatChildren, backAction, parentDisposable);
  }

  public static Pair<JPanel, JBList<AnAction>> createActionGroupPanel(@NotNull List<AnAction> groups,
                                                                      @Nullable Runnable backAction,
                                                                      @NotNull Disposable parentDisposable) {
    JPanel actionsListPanel = new JPanel(new BorderLayout());

    actionsListPanel.setBackground(getProjectsBackground());
    DefaultListModel<AnAction> model = JBList.createDefaultListModel(groups);
    JBList<AnAction> list = new JBList<>(model);
    list.setSelectionModel(new SingleSelectionModel());
    ListListenerCollapsedActionGroupExpander.expandCollapsableGroupsOnSelection(list, model, parentDisposable);
    for (AnAction group : groups) {
      if (group instanceof Disposable) {
        Disposer.register(parentDisposable, (Disposable)group);
      }
    }
    Disposer.register(parentDisposable, new Disposable() {
      @Override
      public void dispose() {
        model.clear();
      }
    });

    list.setBackground(getProjectsBackground());
    list.setCellRenderer(new GroupedItemsListRenderer<>(new ListItemDescriptorAdapter<AnAction>() {
                           @Override
                           public @Nullable String getTextFor(AnAction value) {
                             return value.getTemplateText();
                           }

                           @Override
                           public @Nullable String getCaptionAboveOf(AnAction value) {
                             return getParentGroupName(value);
                           }

                           @Override
                           public boolean hasSeparatorAboveOf(AnAction value) {
                             int index = model.indexOf(value);
                             final String parentGroupName = getParentGroupName(value);

                             if (index < 1) return parentGroupName != null;
                             AnAction upper = model.get(index - 1);
                             if (getParentGroupName(upper) == null && parentGroupName != null) return true;

                             return !Objects.equals(getParentGroupName(upper), parentGroupName);
                           }
                         }) {
                           @Override
                           protected JComponent createItemComponent() {
                             JComponent component = super.createItemComponent();
                             myTextLabel.setBorder(JBUI.Borders.empty(5, 0));
                             return component;
                           }

                           @Override
                           protected Color getBackground() {
                             return getProjectsBackground();
                           }

                           @Override
                           public Component getListCellRendererComponent(JList<? extends AnAction> list,
                                                                         AnAction value,
                                                                         int index,
                                                                         boolean isSelected,
                                                                         boolean cellHasFocus) {
                             var component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                             // Collapsable group should be rendered as collapsable button
                             if (value instanceof CollapsedActionGroup actionGroup) {
                               return CollapsedButtonKt.createCollapsedButton(actionGroup, childAction -> {
                                 // To get an action width we set to the component, render it, and see component width
                                 // This approach obeys component spacing, font's size e.t.c
                                 setLabelByAction(childAction);
                                 return component.getPreferredSize().width;
                               });
                             }
                             return component;
                           }

                           @Override
                           protected void customizeComponent(JList<? extends AnAction> list, AnAction value, boolean isSelected) {
                             setLabelByAction(value);
                           }

                           private void setLabelByAction(@NotNull AnAction value) {
                             if (myTextLabel != null) {
                               myTextLabel.setText(value.getTemplateText());
                               myTextLabel.setIcon(value.getTemplatePresentation().getIcon());
                             }
                           }
                         }
    );

    JScrollPane pane = ScrollPaneFactory.createScrollPane(list, true);
    pane.setBackground(getProjectsBackground());
    actionsListPanel.add(pane, BorderLayout.CENTER);

    int width = (int)MathUtil.clamp(Math.round(list.getPreferredSize().getWidth()), JBUIScale.scale(100), JBUIScale.scale(200));
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

  private static @NotNull List<AnAction> flattenActionGroups(@NotNull ActionGroup actionGroup, @NotNull AnActionEvent event) {
    ArrayList<AnAction> groups = new ArrayList<>();
    for (AnAction action : event.getUpdateSession().children(actionGroup)) {
      if (action instanceof ActionGroup g) {
        var groupName = event.getUpdateSession().presentation(g).getText();
        var children = event.getUpdateSession().children(g);
        if (groupName != null) {
          for (AnAction childAction : children) {
            setParentGroupName(groupName, childAction);
          }
        }
        if (!(g instanceof CollapsedActionGroup)) {
          // Some GroupActions shouldn't be added directly, but children must be added instead
          groups.addAll(children);
          continue;
        }
      }
      // Collapse groups and regular actions
      groups.add(action);
    }
    return groups;
  }

  private static @NlsContexts.Separator String getParentGroupName(@NotNull AnAction value) {
    return value.getTemplatePresentation().getClientProperty(GROUP_NAME);
  }

  private static void setParentGroupName(@NlsContexts.Separator @NotNull String groupName, final @NotNull AnAction childAction) {
    childAction.getTemplatePresentation().putClientProperty(GROUP_NAME, groupName);
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
          var flatChildren = flattenActionGroups(action, e);
          e.getUpdateSession().compute(this, "initPanel", ActionUpdateThread.EDT, () -> {
            initPanel(e, flatChildren);
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
