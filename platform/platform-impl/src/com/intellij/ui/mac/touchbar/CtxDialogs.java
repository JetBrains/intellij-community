// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionGroup;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.impl.LaterInvocator;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

class CtxDialogs {
  private static final Logger LOG = Logger.getInstance(CtxDialogs.class);

  private static final int BUTTON_MIN_WIDTH_DLG = 107;
  private static final int BUTTON_BORDER = 16;
  private static final int BUTTON_IMAGE_MARGIN = 2;

  static @Nullable Disposable showWindowActions(@NotNull Component contentPane) {
    DefaultActionGroup actions = new DefaultActionGroup();

    //
    // Collect touchbar actions from components hierarchy.
    //

    List<AnAction> actionList = findAllTouchbarActions(contentPane);
    if (actionList != null)
      actions.addAll(actionList);

    if (LOG.isDebugEnabled()) {
      if (actionList == null || actionList.isEmpty())
        LOG.debug("window '%s' hasn't any touchbar actions", contentPane);
      else {
        List<AnAction> leaves = new ArrayList<>();
        Helpers.collectLeafActions(actions, leaves);
        LOG.debug("show actions of window '%s' (count=%d):", contentPane, leaves.size());
        for (AnAction act : leaves) {
          LOG.debug("\t'%s' | id=%s | %s", act.getTemplateText(), Helpers.getActionId(act), act);
        }
      }
    }

    //
    // Process customizations
    //

    final ModalityState ms = LaterInvocator.getCurrentModalityState();
    final TBPanel.CrossEscInfo crossEscInfo = isCrossEscGroup(actions) ? new TBPanel.CrossEscInfo(true, false) : null;
    final Customizer customizer = new Customizer(crossEscInfo, null/*dialog actions mustn't be closed because of auto-close*/) {
      private final @NotNull WeakReference<Component> myRoot = new WeakReference<>(contentPane);
      @Override
      void onBeforeActionsExpand(@NotNull ActionGroup actionGroup) {
        // NOTE: possible optimization - listen for components hierarchy changes and do traverse only when it was changed
        final List<AnAction> allActs = findAllTouchbarActions(myRoot.get());
        actions.removeAll();
        if (allActs != null) {
          actions.addAll(allActs);
        }
      }
    };
    customizer.addBaseCustomizations((pc, button, presentation)-> {
      // set common properties
      button.setModality(ms);

      // process per-action data from TouchbarActionCustomizations
      boolean isDefault = false;
      final TouchbarActionCustomizations ac = TouchbarActionCustomizations.getCustomizations(button.getAnAction());
      if (ac != null) {
        isDefault = ac.isDefault();
        if (!isDefault && ac.getComponent() instanceof JButton) {
          // also check properties of JButton
          final JButton jb = (JButton)ac.getComponent();
          isDefault = jb.getAction() != null ? jb.getAction().getValue(DialogWrapper.DEFAULT_ACTION) != null : jb.isDefaultButton();
        }

        button.setComponent(ac.getComponent());
        button.setText(ac.isShowText() ? presentation.getText() : null);
        button.setIcon(ac.isShowImage() ? presentation.getIcon() : null);
        if (ac.getComponent() instanceof JButton) // small customization for south-panel dialog buttons
          button.setLayout(BUTTON_MIN_WIDTH_DLG, NSTLibrary.LAYOUT_FLAG_MIN_WIDTH, BUTTON_IMAGE_MARGIN, BUTTON_BORDER);
      } else {
        TouchbarActionCustomizations parentAc = pc == null ? null : pc.getCustomizations();
        button.setIconAndTextFromPresentation(presentation, parentAc);
        button.setComponent(parentAc == null ? null : parentAc.getComponent());
      }

      if (isDefault) {
        button.setColored();
      }
    });

    TouchBarsManager.registerAndShow(contentPane, actions, customizer);

    return () -> {
      LOG.debug("hide actions of window '%s'", contentPane);
      TouchBarsManager.unregister(contentPane);
    };
  }

  private static @Nullable List<AnAction> findAllTouchbarActions(@NotNull Component root) {
    List<AnAction> result = null;
    // by default used TreeTraversal.PRE_ORDER_DFS: each node's subtrees are traversed after the node itself is returned.
    final JBIterable<Component> iter = UIUtil.uiTraverser(root).expandAndFilter(c -> c.isVisible()).traverse();
    for (Component component : iter) {
      if (component instanceof JComponent) {
        final ActionGroup group = Touchbar.getActions((JComponent)component);
        if (group != null) {
          if (result == null) result = new ArrayList<>();
          result.add(group);
        }
      }
    }

    return result;
  }

  private static boolean isCrossEscGroup(@NotNull ActionGroup group) {
    for (AnAction child : group.getChildren(null)) {
      final TouchbarActionCustomizations customizations = TouchbarActionCustomizations.getCustomizations(child);
      if (customizations == null || !customizations.isCrossEsc())
        return false;
    }
    return true;
  }
}
