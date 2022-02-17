// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.evaluate.quick.common;

import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.actionSystem.ex.CustomComponentAction;
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl;
import com.intellij.openapi.keymap.KeymapUtil;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.ClientProperty;
import com.intellij.ui.JBColor;
import com.intellij.ui.WindowMoveListener;
import com.intellij.ui.components.AnActionLink;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.GridBag;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.components.BorderLayoutPanel;
import com.intellij.xdebugger.impl.ui.InplaceEditor;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ContainerEvent;
import java.awt.event.ContainerListener;
import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Locale;

@ApiStatus.Experimental
public abstract class XDebuggerPopupPanel {
  protected final @NotNull BorderLayoutPanel myContent;
  protected final @NotNull BorderLayoutPanel myMainPanel;
  protected final @NotNull ActionToolbarImpl myToolbar;
  private final @NotNull String myToolbarActionsPlace;

  @ApiStatus.Experimental
  protected XDebuggerPopupPanel(@NotNull String toolbarActionsPlace) {
    myToolbarActionsPlace = toolbarActionsPlace;
    myContent = JBUI.Panels.simplePanel();
    myMainPanel = JBUI.Panels.simplePanel();
    myToolbar = createToolbar();

    fillContentPanel();
  }

  @NotNull
  protected DefaultActionGroup getToolbarActions() {
    DefaultActionGroup toolbarActions = new DefaultActionGroup();
    toolbarActions.addSeparator();
    return toolbarActions;
  }

  protected boolean shouldBeVisible(AnAction action) {
    return true;
  }

  protected void setAutoResizeUntilToolbarNotFull(@NotNull Runnable resizeRunnable, Disposable disposable) {
    ContainerListener containerListener = new ContainerListener() {
      @Override
      public void componentAdded(ContainerEvent e) {
        resizeRunnable.run();
      }

      @Override
      public void componentRemoved(ContainerEvent e) {
        resizeRunnable.run();
      }
    };

    myToolbar.addContainerListener(containerListener);
    myToolbar.addListener(new ActionToolbarListener() {
      private boolean myActionsUpdatedOnce = false;

      @Override
      public void actionsUpdated() {
        if (myActionsUpdatedOnce) {
          myToolbar.removeContainerListener(containerListener);
        }
        myActionsUpdatedOnce = true;
      }
    }, disposable);
  }

  private void setToolbarActionsDataProvider(@Nullable Component dataProvider) {
    for (AnAction action : myToolbar.getActions()) {
      if (action instanceof ActionWrapper) {
        ((ActionWrapper)action).setDataProvider(dataProvider);
      }
    }
  }

  protected void setContent(@NotNull JComponent content, @Nullable Component toolbarActionsDataProvider) {
    myMainPanel.addToCenter(content);
    setToolbarActionsDataProvider(toolbarActionsDataProvider);
  }

  @NotNull
  protected ActionToolbarImpl createToolbar() {
    DefaultActionGroup wrappedActions = wrapActions(getToolbarActions());

    var toolbarImpl = new ActionToolbarImpl(myToolbarActionsPlace, wrappedActions, true);
    toolbarImpl.setTargetComponent(null);
    for (AnAction action : wrappedActions.getChildren(null)) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myMainPanel);
    }

    toolbarImpl.setBorder(BorderFactory.createEmptyBorder());
    return toolbarImpl;
  }

  protected void fillContentPanel() {
    myToolbar.setBackground(UIUtil.getToolTipActionBackground());

    WindowMoveListener moveListener = new WindowMoveListener(myContent);
    myToolbar.addMouseListener(moveListener);
    myToolbar.addMouseMotionListener(moveListener);

    myContent
      .addToCenter(myMainPanel)
      .addToBottom(myToolbar);
  }

  @NotNull
  private DefaultActionGroup wrapActions(@NotNull DefaultActionGroup toolbarActions) {
    DefaultActionGroup wrappedActions = new DefaultActionGroup();
    for (AnAction action : toolbarActions.getChildren(null)) {
      ActionWrapper actionLink = new ActionWrapper(action);
      wrappedActions.add(actionLink);
    }

    return wrappedActions;
  }

  private static @NotNull JPanel createCustomToolbarComponent(@NotNull AnAction action, @NotNull AnActionLink actionLink) {
    JPanel actionPanel = new JPanel(new GridBagLayout());

    GridBag gridBag = new GridBag().fillCellHorizontally().anchor(GridBagConstraints.WEST);
    int topInset = 5;
    int bottomInset = 4;

    actionPanel.add(actionLink, gridBag.next().insets(topInset, 10, bottomInset, 4));

    Shortcut[] shortcuts = action.getShortcutSet().getShortcuts();
    String shortcutsText = KeymapUtil.getShortcutsText(shortcuts);
    if (!shortcutsText.isEmpty()) {
      JComponent keymapHint = createKeymapHint(shortcutsText);
      actionPanel.add(keymapHint, gridBag.next().insets(topInset, 4, bottomInset, 12));
    }

    actionPanel.setBackground(UIUtil.getToolTipActionBackground());
    return actionPanel;
  }

  private static JComponent createKeymapHint(@NlsContexts.Label String shortcutRunAction) {
    JBLabel shortcutHint = new JBLabel(shortcutRunAction) {
      @Override
      public Color getForeground() {
        return getKeymapColor();
      }
    };
    shortcutHint.setBorder(JBUI.Borders.empty());
    shortcutHint.setFont(UIUtil.getToolTipFont());
    return shortcutHint;
  }

  private static Color getKeymapColor() {
    return JBColor.namedColor("ToolTip.Actions.infoForeground", new JBColor(0x99a4ad, 0x919191));
  }

  private static JPanel getSecretComponentForToolbar() {
    JPanel secretPanel = new JPanel();
    secretPanel.setPreferredSize(new Dimension(0, 27));
    return secretPanel;
  }

  private static class ActionLinkButton extends AnActionLink {

    ActionLinkButton(@NotNull AnAction action,
                     @NotNull Presentation presentation,
                     @Nullable DataProvider contextComponent) {
      //noinspection ConstantConditions
      super(StringUtil.capitalize(presentation.getText().toLowerCase(Locale.ROOT)), action);
      setDataProvider(contextComponent);
      setFont(UIUtil.getToolTipFont());
    }
  }

  private class ActionWrapper extends AnAction implements CustomComponentAction {
    private final AnAction myDelegate;
    private @Nullable Component myProvider;

    ActionWrapper(AnAction delegate) {
      super(delegate.getTemplateText());
      copyFrom(delegate);
      myDelegate = delegate;
    }

    public void setDataProvider(@Nullable Component provider) {
      myProvider = provider;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
      AnActionEvent delegateEvent = e;
      if (myProvider != null) {
        delegateEvent = AnActionEvent.createFromAnAction(myDelegate,
                                                         e.getInputEvent(),
                                                         myToolbarActionsPlace,
                                                         DataManager.getInstance().getDataContext(myProvider));
      }
      myDelegate.actionPerformed(delegateEvent);
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
      myDelegate.update(e);
      Presentation presentation = e.getPresentation();
      presentation.setEnabled(presentation.isEnabled() && shouldBeVisible(myDelegate));
      presentation.setVisible(presentation.isVisible() && shouldBeVisible(myDelegate));
    }

    @Override
    public boolean isDumbAware() {
      return myDelegate.isDumbAware();
    }

    @Override
    public @NotNull JComponent createCustomComponent(@NotNull Presentation presentation, @NotNull String place) {
      if (myDelegate instanceof Separator) {
        return getSecretComponentForToolbar(); // this is necessary because the toolbar hide if all action panels are not visible
      }

      myDelegate.applyTextOverride(myToolbarActionsPlace, presentation);

      DataProvider dataProvider = myProvider instanceof DataProvider ? (DataProvider)myProvider : null;

      ActionLinkButton button = new ActionLinkButton(this, presentation, dataProvider);
      ClientProperty.put(button, InplaceEditor.IGNORE_MOUSE_EVENT, true);
      JPanel actionPanel = createCustomToolbarComponent(this, button);

      presentation.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (evt.getPropertyName() == Presentation.PROP_TEXT) {
            String value = (String)evt.getNewValue();
            button.setText(StringUtil.capitalize(value.toLowerCase(Locale.ROOT)));
            button.repaint();
          }
          if (evt.getPropertyName() == Presentation.PROP_ENABLED) {
            actionPanel.setVisible((Boolean)evt.getNewValue());
            actionPanel.repaint();
          }
        }
      });
      actionPanel.setVisible(presentation.isEnabled());

      return actionPanel;
    }
  }
}
