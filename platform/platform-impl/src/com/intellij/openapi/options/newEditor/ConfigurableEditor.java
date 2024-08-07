// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEventsKt;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.AnActionResult;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurableGroup;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableCardPanel;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.*;
import com.intellij.util.Alarm;
import com.intellij.util.ObjectUtils;
import com.intellij.util.messages.MessageBusConnection;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.concurrency.Promise;
import org.jetbrains.concurrency.Promises;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.Arrays;

import static com.intellij.openapi.options.newEditor.ConfigurablesListPanelKt.createConfigurablesListPanel;

class ConfigurableEditor extends AbstractEditor implements AnActionListener, AWTEventListener {
  private final MergingUpdateQueue queue = new MergingUpdateQueue("SettingsModification", 1000, false, this, this, this, Alarm.ThreadToUse.SWING_THREAD, coroutineScope);
  private final ConfigurableCardPanel myCardPanel = new ConfigurableCardPanel() {
    @Override
    protected JComponent create(Configurable configurable) {
      JComponent content = super.create(configurable);
      return content != null ? content : createDefaultContent(configurable);
    }
  };
  private final JLabel myErrorLabel = new JLabel();
  private final AbstractAction myApplyAction = new AbstractAction(CommonBundle.getApplyButtonText()) {
    @Override
    public void actionPerformed(ActionEvent event) {
      apply();
    }
  };
  private final AbstractAction myResetAction = new AbstractAction(UIBundle.message("configurable.reset.action.name")) {
    @Override
    public void actionPerformed(ActionEvent event) {
      if (configurable != null) {
        ConfigurableCardPanel.reset(configurable);
        updateCurrent(configurable, true);
        FeatureUsageUiEventsKt.getUiEventLogger().logResetConfigurable(configurable);
      }
    }
  };
  private Configurable configurable;

  ConfigurableEditor(Disposable parent) {
    super(parent);
  }

  ConfigurableEditor(Disposable parent, Configurable configurable) {
    super(parent);
    init(configurable, parent instanceof SettingsEditor);
  }

  protected void init(Configurable configurable, boolean enableError) {
    myApplyAction.setEnabled(false);
    myResetAction.putValue(Action.SHORT_DESCRIPTION, UIBundle.message("configurable.reset.action.description"));
    myResetAction.setEnabled(false);
    myErrorLabel.setOpaque(true);
    myErrorLabel.setEnabled(enableError);
    myErrorLabel.setVisible(false);
    myErrorLabel.setVerticalTextPosition(SwingConstants.TOP);
    myErrorLabel.setBorder(JBUI.Borders.empty(10, 15, 15, 15));
    myErrorLabel.setBackground(LightColors.RED);
    add(BorderLayout.SOUTH, RelativeFont.HUGE.install(myErrorLabel));
    add(BorderLayout.CENTER, myCardPanel);
    Disposer.register(this, myCardPanel);
    MessageBusConnection messageBus = ApplicationManager.getApplication().getMessageBus().connect(this);
    messageBus.subscribe(AnActionListener.TOPIC, this);
    messageBus.subscribe(ExternalUpdateRequest.TOPIC, conf -> updateCurrent(conf, false));
    Toolkit.getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    if (configurable != null) {
      this.configurable = configurable;
      myCardPanel.select(configurable, true).doWhenDone(() -> postUpdateCurrent(configurable));
    }
    updateCurrent(configurable, false);
  }

  @Override
  protected void disposeOnce() {
    Toolkit.getDefaultToolkit().removeAWTEventListener(this);
    myCardPanel.removeAll();
  }

  @Override
  protected String getHelpTopic() {
    return configurable == null ? null : configurable.getHelpTopic();
  }

  @Override
  protected Action getApplyAction() {
    return myApplyAction;
  }

  @Override
  protected Action getResetAction() {
    return myResetAction;
  }

  @Override
  protected boolean apply() {
    // do not apply changes of a single configurable if it is not modified
    updateIfCurrent(configurable);
    return setError(apply(myApplyAction.isEnabled() ? configurable : null));
  }

  @Override
  protected boolean cancel(AWTEvent source) {
    configurable.cancel();
    return super.cancel(source);
  }

  void openLink(Configurable configurable) {
    ShowSettingsUtil.getInstance().editConfigurable(this, configurable);
  }

  @Override
  public final void beforeEditorTyping(char ch, @NotNull DataContext context) {
  }

  @Override
  public final void beforeActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event) {
  }

  @Override
  public final void afterActionPerformed(@NotNull AnAction action, @NotNull AnActionEvent event, @NotNull AnActionResult result) {
    requestUpdate();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    if (configurable == null) return null; // settings editor is not configured yet
    JComponent preferred = configurable.getPreferredFocusedComponent();
    return preferred == null ? UIUtil.getPreferredFocusedComponent(getContent(configurable)) : preferred;
  }

  @Override
  public final void eventDispatched(AWTEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED, MouseEvent.MOUSE_RELEASED, MouseEvent.MOUSE_DRAGGED -> {
        MouseEvent me = (MouseEvent)event;
        if (SwingUtilities.isDescendingFrom(me.getComponent(), this) || isPopupOverEditor(me.getComponent())) {
          requestUpdate();
        }
      }
      case KeyEvent.KEY_PRESSED, KeyEvent.KEY_RELEASED -> {
        KeyEvent ke = (KeyEvent)event;
        if (SwingUtilities.isDescendingFrom(ke.getComponent(), this)) {
          requestUpdate();
        }
      }
    }
  }

  void requestUpdate() {
    Configurable configurable = this.configurable;
    queue.queue(new Update(this) {
      @Override
      public void run() {
        updateIfCurrent(configurable);
      }

      @Override
      public boolean isExpired() {
        return ConfigurableEditor.this.configurable != configurable;
      }
    });
  }

  private boolean isPopupOverEditor(Component component) {
    Window editor = ComponentUtil.getWindow(this);
    if (editor != null) {
      Window popup = ComponentUtil.getWindow(component);
      // light-weight popup is located on the layered pane of the same window
      if (popup == editor) {
        return true;
      }
      // heavy-weight popup opens new window with the corresponding parent
      if (popup != null && editor == popup.getParent()) {
        if (popup instanceof JDialog dialog) {
          return Dialog.ModalityType.MODELESS == dialog.getModalityType();
        }
        return popup instanceof JWindow;
      }
    }
    return false;
  }

  void updateCurrent(Configurable configurable, boolean reset) {
    boolean modified = configurable != null && configurable.isModified();
    myApplyAction.setEnabled(modified);
    myResetAction.setEnabled(modified);
    if (!modified && reset) {
      setError(null);
    }
  }

  void postUpdateCurrent(Configurable configurable) {
  }

  final void updateIfCurrent(Configurable configurable) {
    if (this.configurable == configurable) {
      updateCurrent(configurable, false);
    }
  }

  final @NotNull Promise<? super Object> select(final Configurable configurable) {
    assert !isDisposed : "Already disposed";
    long startTime = System.currentTimeMillis();
    final boolean loadedFromCache = myCardPanel.getValue(configurable, false) != null;
    ActionCallback callback = myCardPanel.select(configurable, false);
    callback
      .doWhenDone(() -> {
        if (this.configurable != configurable) {
          SpotlightPainter.Companion.allowScrolling(this);
        }
        this.configurable = configurable;
        updateCurrent(configurable, false);
        postUpdateCurrent(configurable);
        if (configurable != null) {
          FeatureUsageUiEventsKt.getUiEventLogger().logSelectConfigurable(configurable, loadedFromCache, System.currentTimeMillis() - startTime);
        }
      });
    return Promises.toPromise(callback);
  }

  final boolean setError(ConfigurationException exception) {
    if (exception == null) {
      myErrorLabel.setVisible(false);
      return true;
    }
    if (myErrorLabel.isEnabled()) {
      myErrorLabel.setText(HtmlChunk.body().children(
        HtmlChunk.text(exception.getTitle()).wrapWith("strong"),
        HtmlChunk.text(":"),
        HtmlChunk.br(),
        exception.getMessageHtml()
      ).wrapWith("html").toString());
      myErrorLabel.setVisible(true);
    }
    else {
      Messages.showMessageDialog(this, exception.getMessage(), exception.getTitle(), Messages.getErrorIcon());
    }
    return false;
  }

  final JComponent getContent(Configurable configurable) {
    return myCardPanel.getValue(configurable, false);
  }

  final JComponent readContent(Configurable configurable) {
    return myCardPanel.getValue(configurable, true);
  }

  private JComponent createDefaultContent(Configurable configurable) {
    JComponent content = new JPanel(new BorderLayout());
    content.setBorder(JBUI.Borders.empty(11, 16, 16, 16));

    Configurable.Composite compositeGroup = ObjectUtils.tryCast(configurable, Configurable.Composite.class);
    if (compositeGroup == null) {
      String description = IdeBundle.message("label.select.configuration.element");
      content.add(BorderLayout.CENTER, new JLabel(description, SwingConstants.CENTER));
      content.setPreferredSize(JBUI.size(800, 600));
    }
    else {
      ConfigurableGroup configurableGroup = ConfigurableWrapper.cast(ConfigurableGroup.class, configurable);
      String description = configurableGroup != null ? configurableGroup.getDescription() : null;
      content.add(BorderLayout.CENTER, createConfigurablesListPanel(description, Arrays.asList(compositeGroup.getConfigurables()), this));
    }

    JScrollPane pane = ScrollPaneFactory.createScrollPane(content, true);
    pane.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
    return pane;
  }

  static ConfigurationException apply(Configurable configurable) {
    if (configurable != null) {
      try {
        configurable.apply();
        FeatureUsageUiEventsKt.getUiEventLogger().logApplyConfigurable(configurable);
      }
      catch (ConfigurationException exception) {
        return exception;
      }
    }
    return null;
  }

  @Nullable
  Configurable getConfigurable() {
    return configurable;
  }

  void reload() {
    myCardPanel.removeAll();
    configurable = null;
  }
}
