// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.welcomeScreen;

import com.intellij.CommonBundle;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.popup.ListItemDescriptorAdapter;
import com.intellij.openapi.ui.popup.StackingPopupDispatcher;
import com.intellij.openapi.util.*;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.wm.IdeFocusManager;
import com.intellij.openapi.wm.WindowManager;
import com.intellij.ui.*;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.panels.NonOpaquePanel;
import com.intellij.ui.popup.list.GroupedItemsListRenderer;
import com.intellij.ui.scale.JBUIScale;
import com.intellij.util.MathUtil;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import static com.intellij.openapi.wm.impl.welcomeScreen.FlatWelcomeFrame.getPreferredFocusedComponent;
import static com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager.getProjectsBackground;

public class ActionGroupPanelWrapper {

  private static final String ACTION_GROUP_KEY = "ACTION_GROUP_KEY";

  public static Pair<JPanel, JBList<AnAction>> createActionGroupPanel(final ActionGroup action,
                                                                      final Runnable backAction,
                                                                      @NotNull Disposable parentDisposable) {
    JPanel actionsListPanel = new JPanel(new BorderLayout());

    actionsListPanel.setBackground(getProjectsBackground());
    final java.util.List<AnAction> groups = flattenActionGroups(action);
    final DefaultListModel<AnAction> model = JBList.createDefaultListModel(groups);
    final JBList<AnAction> list = new JBList<>(model);
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
                           @Nullable
                           @Override
                           public String getTextFor(AnAction value) {
                             return value.getTemplateText();
                           }

                           @Nullable
                           @Override
                           public String getCaptionAboveOf(AnAction value) {
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
                             myTextLabel = new ErrorLabel();
                             myTextLabel.setOpaque(true);
                             myTextLabel.setBorder(JBUI.Borders.empty(3, 7));

                             return myTextLabel;
                           }

                           @Override
                           protected Color getBackground() {
                             return getProjectsBackground();
                           }

                           @Override
                           protected void customizeComponent(JList<? extends AnAction> list, AnAction value, boolean isSelected) {
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
    final JPanel main = new NonOpaquePanel(new BorderLayout());
    main.add(actionsListPanel, BorderLayout.WEST);

    JPanel bottomPanel = new NonOpaquePanel(new FlowLayout(FlowLayout.RIGHT));
    bottomPanel.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, new JBColor(Gray._217, Gray._81)));
    main.add(bottomPanel, BorderLayout.SOUTH);

    final HashMap<Object, JPanel> panelsMap = new HashMap<>();
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

        updateBottomPanel(panel, (AbstractActionWithPanel)value, bottomPanel, backAction);

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
                                        @NotNull AbstractActionWithPanel actionWithPanel,
                                        @NotNull JPanel bottomPanel,
                                        @Nullable Runnable backAction) {
    bottomPanel.removeAll();

    if (SystemInfo.isMac) {
      addCancelButton(bottomPanel, backAction);
      addActionButton(bottomPanel, actionWithPanel, currentPanel);
    }
    else {
      addActionButton(bottomPanel, actionWithPanel, currentPanel);
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
                                      @NotNull AbstractActionWithPanel actionWithPanel,
                                      @NotNull JPanel currentPanel) {
    JButton actionButton = actionWithPanel.getActionButton();
    bottomPanel.add(actionButton);
    currentPanel.getRootPane().setDefaultButton(actionButton);
  }

  @Nullable
  private static JComponent createCancelButton(@Nullable Runnable cancelAction) {
    if (cancelAction == null) return null;

    JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
    cancelButton.addActionListener(e -> cancelAction.run());

    return cancelButton;
  }

  public static void installQuickSearch(JBList<? extends AnAction> list) {
    new ListSpeedSearch<>(list, o -> {
      if (o instanceof AbstractActionWithPanel) { //to avoid dependency mess with ProjectSettingsStepBase
        return o.getTemplatePresentation().getText();
      }
      return null;
    });
  }

  private static List<AnAction> flattenActionGroups(@NotNull final ActionGroup action) {
    final ArrayList<AnAction> groups = new ArrayList<>();
    String groupName;
    for (AnAction anAction : action.getChildren(null)) {
      if (anAction instanceof ActionGroup) {
        groupName = anAction.getTemplateText();
        for (AnAction childAction : ((ActionGroup)anAction).getChildren(null)) {
          if (groupName != null) {
            setParentGroupName(groupName, childAction);
          }
          groups.add(childAction);
        }
      }
      else {
        groups.add(anAction);
      }
    }
    return groups;
  }

  private static @NlsContexts.Separator String getParentGroupName(@NotNull final AnAction value) {
    return (String)value.getTemplatePresentation().getClientProperty(ACTION_GROUP_KEY);
  }

  private static void setParentGroupName(@NotNull final String groupName, @NotNull final AnAction childAction) {
    childAction.getTemplatePresentation().putClientProperty(ACTION_GROUP_KEY, groupName);
  }

  public static AnAction wrapGroups(@NotNull AnAction action, @NotNull Disposable parentDisposable) {
    if (!(action instanceof ActionGroup)) return action;
    if (action instanceof ActionsWithPanelProvider) {
      AtomicReference<Component> createdPanel = new AtomicReference<>();
      final Pair<JPanel, JBList<AnAction>> panel =
        createActionGroupPanel((ActionGroup)action, () -> goBack(createdPanel.get()), parentDisposable);
      createdPanel.set(panel.first);
      final Runnable onDone = () -> {
        if (action.getTemplateText() != null) {
          setTitle(StringUtil.removeEllipsisSuffix(action.getTemplateText()));
        }
        final JBList<AnAction> list = panel.second;
        ScrollingUtil.ensureSelectionExists(list);
        final ListSelectionListener[] listeners =
          ((DefaultListSelectionModel)list.getSelectionModel()).getListeners(ListSelectionListener.class);

        //avoid component cashing. This helps in case of LaF change
        for (ListSelectionListener listener : listeners) {
          listener.valueChanged(new ListSelectionEvent(list, list.getSelectedIndex(), list.getSelectedIndex(), false));
        }
        JComponent toFocus = getPreferredFocusedComponent(panel);
        IdeFocusManager.getGlobalInstance().doWhenFocusSettlesDown(() -> IdeFocusManager.getGlobalInstance().requestFocus(toFocus, true));
      };
      panel.first.setName(action.getClass().getName());
      final Presentation p = action.getTemplatePresentation();
      return new DumbAwareAction(p.getText(), p.getDescription(), p.getIcon()) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          ApplicationManager.getApplication().getMessageBus().syncPublisher(WelcomeScreenComponentListener.COMPONENT_CHANGED)
            .attachComponent(panel.first, onDone);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          action.update(e);
        }
      };
    }
    return action;
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
