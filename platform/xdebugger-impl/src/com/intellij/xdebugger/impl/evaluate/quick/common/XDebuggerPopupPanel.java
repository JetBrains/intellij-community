// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.ui.ScreenUtil;
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
@ApiStatus.Internal
public abstract class XDebuggerPopupPanel {
  protected final @NotNull BorderLayoutPanel myContent = JBUI.Panels.simplePanel();
  protected final @NotNull BorderLayoutPanel myMainPanel = JBUI.Panels.simplePanel();
  protected ActionToolbarImpl myToolbar = null;

  protected boolean shouldBeVisible(AnAction action) {
    return true;
  }

  protected final void setAutoResizeUntilToolbarNotFull(@NotNull Runnable resizeRunnable, Disposable disposable) {
    if (myToolbar == null) return;

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

  protected static void updatePopupBounds(@NotNull Window popupWindow, int newWidth, int newHeight) {
    final Rectangle screenRectangle = ScreenUtil.getScreenRectangle(popupWindow);

    // shift the x coordinate if there is not enough space on the right
    Point location = popupWindow.getLocation();
    int delta = (location.x + newWidth) - (screenRectangle.x + screenRectangle.width);
    location.x -= Math.max(delta, 0);
    location.x = Math.max(location.x, screenRectangle.x);

    final Rectangle targetBounds = new Rectangle(location.x, location.y, newWidth, newHeight);

    ScreenUtil.cropRectangleToFitTheScreen(targetBounds);
    if (targetBounds.height != popupWindow.getHeight() || targetBounds.width != popupWindow.getWidth()) {
      popupWindow.setBounds(targetBounds);
      popupWindow.validate();
      popupWindow.repaint();
    }
  }

  protected final void setContent(@NotNull JComponent content,
                                  @NotNull DefaultActionGroup toolbarActions,
                                  @NotNull String actionsPlace,
                                  @Nullable Component toolbarActionsDataProvider) {
    myToolbar = createToolbar(toolbarActions, actionsPlace, toolbarActionsDataProvider);
    fillContentPanel(content, myToolbar);
  }

  private @NotNull ActionToolbarImpl createToolbar(@NotNull DefaultActionGroup toolbarActions,
                                                   @NotNull String actionsPlace,
                                                   @Nullable Component toolbarActionsDataProvider) {
    toolbarActions.add(new Separator());
    DefaultActionGroup wrappedActions = wrapActions(toolbarActions, actionsPlace, toolbarActionsDataProvider);

    var toolbarImpl = new ActionToolbarImpl(actionsPlace, wrappedActions, true);
    toolbarImpl.setTargetComponent(null);
    for (AnAction action : wrappedActions.getChildren(ActionManager.getInstance())) {
      action.registerCustomShortcutSet(action.getShortcutSet(), myMainPanel);
    }

    toolbarImpl.setBorder(BorderFactory.createEmptyBorder());
    return toolbarImpl;
  }

  private void fillContentPanel(@NotNull JComponent content, @NotNull ActionToolbarImpl toolbar) {
    myMainPanel.addToCenter(content);
    toolbar.setBackground(UIUtil.getToolTipActionBackground());

    new WindowMoveListener(myContent).installTo(toolbar);

    myContent
      .addToCenter(myMainPanel)
      .addToBottom(toolbar);
  }

  private @NotNull DefaultActionGroup wrapActions(@NotNull DefaultActionGroup toolbarActions,
                                                  @NotNull String actionsPlace,
                                                  @Nullable Component toolbarActionsDataProvider) {
    DefaultActionGroup wrappedActions = new DefaultActionGroup();
    for (AnAction action : toolbarActions.getChildren(ActionManager.getInstance())) {
      ActionWrapper actionLink = new ActionWrapper(action, actionsPlace);
      actionLink.setDataProvider(toolbarActionsDataProvider);
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

    final Component contextComponent;

    ActionLinkButton(@NotNull AnAction action,
                     @NotNull Presentation presentation,
                     @Nullable Component contextComponent) {
      super(StringUtil.capitalize(presentation.getText().toLowerCase(Locale.ROOT)), action);
      this.contextComponent = contextComponent;
      setFont(UIUtil.getToolTipFont());
    }

    @Override
    public void uiDataSnapshot(@NotNull DataSink sink) {
      super.uiDataSnapshot(sink);
      DataSink.uiDataSnapshot(sink, contextComponent);
    }
  }

  private class ActionWrapper extends AnAction implements CustomComponentAction {
    private final AnAction myDelegate;
    private final @NotNull String myActionPlace;
    private @Nullable Component myProvider;

    ActionWrapper(AnAction delegate, @NotNull String actionPlace) {
      super(delegate.getTemplateText());
      copyFrom(delegate);
      myDelegate = delegate;
      myActionPlace = actionPlace;
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
                                                         myActionPlace,
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
    public @NotNull ActionUpdateThread getActionUpdateThread() {
      return myDelegate.getActionUpdateThread();
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

      myDelegate.applyTextOverride(myActionPlace, presentation);

      ActionLinkButton button = new ActionLinkButton(this, presentation, myProvider);
      ClientProperty.put(button, InplaceEditor.IGNORE_MOUSE_EVENT, true);
      JPanel actionPanel = createCustomToolbarComponent(this, button);

      presentation.addPropertyChangeListener(new PropertyChangeListener() {
        @Override
        public void propertyChange(PropertyChangeEvent evt) {
          if (Presentation.PROP_TEXT.equals(evt.getPropertyName())) {
            String value = (String)evt.getNewValue();
            button.setText(StringUtil.capitalize(value.toLowerCase(Locale.ROOT)));
            button.repaint();
          }
          if (Presentation.PROP_ENABLED.equals(evt.getPropertyName())) {
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
