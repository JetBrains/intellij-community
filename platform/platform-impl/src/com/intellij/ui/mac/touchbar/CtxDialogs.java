// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.OptionAction;
import com.intellij.ui.components.JBOptionButton;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.util.List;
import java.util.*;

class CtxDialogs {
  private static final Logger LOG = Logger.getInstance(CtxDialogs.class);
  private static final boolean EXPAND_OPTION_BUTTONS = Boolean.getBoolean("touchbar.dialogs.expand.option.button");

  private static final int BUTTON_MIN_WIDTH_DLG = 107;
  private static final int BUTTON_BORDER = 16;
  private static final int BUTTON_IMAGE_MARGIN = 2;

  static @Nullable Disposable showDialogButtons(@NotNull Container contentPane) {
    Map<TouchbarDataKeys.DlgButtonDesc, JButton> buttonMap = new HashMap<>();
    Map<Component, ActionGroup> actions = new HashMap<>();
    _findAllTouchbarProviders(actions, buttonMap, contentPane);

    if (buttonMap.isEmpty() && actions.isEmpty()) {
      return null;
    }

    @Nullable TouchBar.CrossEscInfo crossEscInfo = null;
    if (!actions.isEmpty()) {
      ActionGroup actionGroup = actions.values().iterator().next();
      TouchbarDataKeys.ActionDesc groupDesc = actionGroup.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      if (groupDesc == null || groupDesc.isReplaceEsc()) {
        crossEscInfo = new TouchBar.CrossEscInfo(true, false);
      }
    }

    LOG.debug("show actions of dialog '%s', south-buttons count=%d, content actions count=%d", contentPane, buttonMap.size(), actions.size());

    Customizer customizations = new Customizer(crossEscInfo, null/*dialog actions mustn't be closed because of auto-close*/);
    ActionGroup ag = buildDialogButtonsGroup(buttonMap, actions, customizations);
    TouchBarsManager.registerAndShow(contentPane, ag, customizations);

    return () -> {
      LOG.debug("hide actions of dialog '%s'", contentPane);
      TouchBarsManager.unregister(contentPane);
    };
  }

  private static void _findAllTouchbarProviders(@NotNull Map<Component, ActionGroup> out,
                                                @NotNull Map<TouchbarDataKeys.DlgButtonDesc, JButton> out2,
                                                @NotNull Container root) {
    final JBIterable<Component> iter = UIUtil.uiTraverser(root).expandAndFilter(c -> c.isVisible()).traverse();
    for (Component component : iter) {
      if (component instanceof JButton) {
        final TouchbarDataKeys.DlgButtonDesc desc = UIUtil.getClientProperty(component, TouchbarDataKeys.DIALOG_BUTTON_DESCRIPTOR_KEY);
        if (desc != null) {
          out2.put(desc, (JButton)component);
        }
      }

      DataProvider dp = null;
      if (component instanceof DataProvider) {
        dp = (DataProvider)component;
      }
      else if (component instanceof JComponent) {
        dp = DataManager.getDataProvider((JComponent)component);
      }

      if (dp != null) {
        final ActionGroup actions = TouchbarDataKeys.ACTIONS_KEY.getData(dp);
        if (actions != null) {
          out.put(component, actions);
        }
      }
    }
  }

  private static ActionGroup buildDialogButtonsGroup(
    @Nullable Map<TouchbarDataKeys.DlgButtonDesc, JButton> unorderedButtons,
    @Nullable Map<Component, ActionGroup> actions,
    @NotNull Customizer customizations /*out*/) {
    final boolean hasSouthPanelButtons = unorderedButtons != null && !unorderedButtons.isEmpty();
    byte prio = -1;

    final ModalityState ms = LaterInvocator.getCurrentModalityState();
    final DefaultActionGroup result = new DefaultActionGroup();

    // 1. add (at left) option buttons of dialog (from south panel)
    if (EXPAND_OPTION_BUTTONS && hasSouthPanelButtons) {
      for (JButton jb : unorderedButtons.values()) {
        if (jb instanceof JBOptionButton) {
          final JBOptionButton ob = (JBOptionButton)jb;
          final Action[] opts = ob.getOptions();
          if (opts != null) {
            for (Action a : opts) {
              if (a == null) {
                continue;
              }
              final AnAction anAct = _createAnAction(a, ob, true);
              if (anAct == null) {
                continue;
              }

              result.add(anAct);
              final byte actPriority = --prio;
              customizations.addCustomization(anAct, (parent, b, pres) -> {
                b.setModality(ms);
                b.setComponent(ob);

                b.setPriority(actPriority);
                b.setText(pres.getText());
                b.setIcon(null);
                b.setLayout(BUTTON_MIN_WIDTH_DLG, NSTLibrary.LAYOUT_FLAG_MIN_WIDTH, BUTTON_IMAGE_MARGIN, BUTTON_BORDER);
              });
            }
          }
        }
      }
    }

    // 2. add actions (collected from content children) before principal group
    if (actions != null && !actions.isEmpty()) {
      final ActionGroup ag = actions.values().iterator().next();

      final TouchbarDataKeys.ActionDesc groupDesc = ag.getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
      if (
        !hasSouthPanelButtons ||
        (groupDesc != null && groupDesc.isCombineWithDlgButtons())
      ) {

        final Map<AnAction, Integer> act2Priority = new HashMap<>();
        final AnAction[] children = ag.getChildren(null);
        for (AnAction act: children) {
          act2Priority.put(act, (int)--prio);
        }
        result.add(ag);
        customizations.addGroupCustomization(ag, (parentInfo, butt, presentation) -> {
          Integer p = act2Priority.get(butt.getAnAction());
          if (p != null) {
            butt.setPriority((byte)(p & 0xff));
          }

          TouchbarDataKeys.ActionDesc pd = parentInfo == null ? null : parentInfo.getDesc();
          butt.setIconAndTextFromPresentation(presentation, pd == null ? groupDesc : pd);

          butt.setModality(ms);

          final TouchbarDataKeys.ActionDesc actionDesc =
            butt.getAnAction().getTemplatePresentation().getClientProperty(TouchbarDataKeys.ACTIONS_DESCRIPTOR_KEY);
          if (actionDesc != null && actionDesc.getContextComponent() != null) {
            butt.setComponent(actionDesc.getContextComponent());
          }
        });

        // NOTE: originally it was ag.addPropertyChangeListener
        // but now it's unnecessary, because we always expand action group every 500ms
      }
    }

    // 3. add left and main buttons (and make main principal)
    final List<TouchbarDataKeys.DlgButtonDesc> orderedSouthButtons = _extractOrderedButtons(unorderedButtons);
    if (orderedSouthButtons != null) {
      for (TouchbarDataKeys.DlgButtonDesc desc : orderedSouthButtons) {
        final JButton jb = unorderedButtons.get(desc);

        // NOTE: can be true: jb.getAction().isEnabled() && !jb.isEnabled()
        final AnAction anAct = _createAnAction(jb.getAction(), jb, false);
        if (anAct == null) {
          continue;
        }

        result.add(anAct);
        final byte actPriority = --prio;
        customizations.addCustomization(anAct, (parent, b, pres) -> {
          b.setModality(ms);
          b.setComponent(jb);

          b.setPriority(actPriority);
          b.setText(pres.getText());
          b.setIcon(null);
          b.setLayout(BUTTON_MIN_WIDTH_DLG, NSTLibrary.LAYOUT_FLAG_MIN_WIDTH, BUTTON_IMAGE_MARGIN, BUTTON_BORDER);
          boolean isDefault = desc.isDefault();
          if (!isDefault) {
            // check other properties
            isDefault = jb.getAction() != null ? jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null : jb.isDefaultButton();
          }
          if (isDefault) {
            b.setColored();
          }
        });
        customizations.addDescriptor(anAct,desc);
      }
    }

    return result;
  }

  private static AnAction _createAnAction(@Nullable Action action,
                                          @NotNull JButton fromButton,
                                          boolean useTextFromAction /*for optional buttons*/) {
    final Object anAct = action == null ? null : action.getValue(OptionAction.AN_ACTION);
    if (anAct == null) {
      // LOG.warn("null AnAction in action: '" + action + "', use wrapper");
      return new DumbAwareAction() {
        {
          setEnabledInModalContext(true);
          if (useTextFromAction) {
            final Object name = action == null ? fromButton.getText() : action.getValue(Action.NAME);
            getTemplatePresentation().setText(name instanceof String ? (String)name : "");
          }
        }

        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          if (action == null) {
            fromButton.doClick();
            return;
          }
          // also can be used something like: ApplicationManager.getApplication().invokeLater(() -> jb.doClick(), ms)
          action.actionPerformed(new ActionEvent(fromButton, ActionEvent.ACTION_PERFORMED, null));
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          e.getPresentation().setEnabled(action == null ? fromButton.isEnabled() : action.isEnabled());
          if (!useTextFromAction) {
            e.getPresentation().setText(DialogWrapper.extractMnemonic(fromButton.getText()).second);
          }
        }
      };
    }
    if (!(anAct instanceof AnAction)) {
      // LOG.warn("unknown type of awt.Action's property: " + anAct.getClass().toString());
      return null;
    }
    return (AnAction)anAct;
  }

  private static @Nullable List<TouchbarDataKeys.DlgButtonDesc> _extractOrderedButtons(@Nullable Map<TouchbarDataKeys.DlgButtonDesc, JButton> unorderedButtons) {
    if (unorderedButtons == null || unorderedButtons.isEmpty()) {
      return null;
    }

    final List<TouchbarDataKeys.DlgButtonDesc> main = new ArrayList<>();
    final List<TouchbarDataKeys.DlgButtonDesc> secondary = new ArrayList<>();
    for (Map.Entry<TouchbarDataKeys.DlgButtonDesc, JButton> entry : unorderedButtons.entrySet()) {
      final TouchbarDataKeys.DlgButtonDesc jbdesc = entry.getKey();
      if (jbdesc == null) {
        continue;
      }

      if (jbdesc.isMainGroup()) {
        main.add(jbdesc);
      }
      else {
        secondary.add(jbdesc);
      }
    }

    final Comparator<TouchbarDataKeys.DlgButtonDesc> cmp = Comparator.comparingInt(TouchbarDataKeys.DlgButtonDesc::getOrder);
    main.sort(cmp);
    secondary.sort(cmp);
    return ContainerUtil.concat(secondary, main);
  }
}
