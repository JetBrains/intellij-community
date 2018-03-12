// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.options.newEditor;

import com.intellij.CommonBundle;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.internal.statistic.eventLog.FeatureUsageUiEvents;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.ConfigurableCardPanel;
import com.intellij.openapi.options.ex.ConfigurableExtensionPointUtil;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.JBColor;
import com.intellij.ui.RelativeFont;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.containers.JBIterable;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.ResourceBundle;

import static com.intellij.ui.ScrollPaneFactory.createScrollPane;
import static java.awt.Toolkit.getDefaultToolkit;
import static javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER;
import static javax.swing.SwingUtilities.isDescendingFrom;

/**
 * @author Sergey.Malenkov
 */
class ConfigurableEditor extends AbstractEditor implements AnActionListener, AWTEventListener {
  private static final JBColor ERROR_BACKGROUND = new JBColor(0xffbfbf, 0x591f1f);
  private static final String RESET_NAME = "Reset";
  private static final String RESET_DESCRIPTION = "Rollback changes for this configuration element";
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("SettingsModification", 1000, false, this, this, this);
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
  private final AbstractAction myResetAction = new AbstractAction(RESET_NAME) {
    @Override
    public void actionPerformed(ActionEvent event) {
      if (myConfigurable != null) {
        ConfigurableCardPanel.reset(myConfigurable);
        updateCurrent(myConfigurable, true);
        FeatureUsageUiEvents.INSTANCE.logResetConfigurable(getConfigurableEventId(myConfigurable));
      }
    }
  };
  private Configurable myConfigurable;

  ConfigurableEditor(Disposable parent) {
    super(parent);
  }

  ConfigurableEditor(Disposable parent, Configurable configurable) {
    super(parent);
    init(configurable, parent instanceof SettingsEditor);
  }

  protected void init(Configurable configurable, boolean enableError) {
    myApplyAction.setEnabled(false);
    myResetAction.putValue(Action.SHORT_DESCRIPTION, RESET_DESCRIPTION);
    myResetAction.setEnabled(false);
    myErrorLabel.setOpaque(true);
    myErrorLabel.setEnabled(enableError);
    myErrorLabel.setVisible(false);
    myErrorLabel.setVerticalTextPosition(SwingConstants.TOP);
    myErrorLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));
    myErrorLabel.setBackground(ERROR_BACKGROUND);
    add(BorderLayout.SOUTH, RelativeFont.HUGE.install(myErrorLabel));
    add(BorderLayout.CENTER, myCardPanel);
    Disposer.register(this, myCardPanel);
    ActionManager.getInstance().addAnActionListener(this, this);
    getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    if (configurable != null) {
      myConfigurable = configurable;
      myCardPanel.select(configurable, true).doWhenDone(() -> postUpdateCurrent(configurable));
    }
    updateCurrent(configurable, false);
  }

  @Override
  void disposeOnce() {
    getDefaultToolkit().removeAWTEventListener(this);
    myCardPanel.removeAll();
  }

  @Override
  String getHelpTopic() {
    return myConfigurable == null ? null : myConfigurable.getHelpTopic();
  }

  @Override
  Action getApplyAction() {
    return myApplyAction;
  }

  @Override
  Action getResetAction() {
    return myResetAction;
  }

  @Override
  boolean apply() {
    // do not apply changes of a single configurable if it is not modified
    updateIfCurrent(myConfigurable);
    return setError(apply(myApplyAction.isEnabled() ? myConfigurable : null));
  }

  void openLink(Configurable configurable) {
    ShowSettingsUtil.getInstance().editConfigurable(this, configurable);
  }

  @Override
  public final void beforeEditorTyping(char ch, DataContext context) {
  }

  @Override
  public final void beforeActionPerformed(AnAction action, DataContext context, AnActionEvent event) {
  }

  @Override
  public final void afterActionPerformed(AnAction action, DataContext context, AnActionEvent event) {
    requestUpdate();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    JComponent preferred = myConfigurable.getPreferredFocusedComponent();
    return preferred == null ? UIUtil.getPreferredFocusedComponent(getContent(myConfigurable)) : preferred;
  }

  @Override
  public final void eventDispatched(AWTEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
      case MouseEvent.MOUSE_RELEASED:
      case MouseEvent.MOUSE_DRAGGED:
        MouseEvent me = (MouseEvent)event;
        if (isDescendingFrom(me.getComponent(), this) || isPopupOverEditor(me.getComponent())) {
          requestUpdate();
        }
        break;
      case KeyEvent.KEY_PRESSED:
      case KeyEvent.KEY_RELEASED:
        KeyEvent ke = (KeyEvent)event;
        if (isDescendingFrom(ke.getComponent(), this)) {
          requestUpdate();
        }
        break;
    }
  }

  void requestUpdate() {
    final Configurable configurable = myConfigurable;
    myQueue.queue(new Update(this) {
      @Override
      public void run() {
        updateIfCurrent(configurable);
      }

      @Override
      public boolean isExpired() {
        return myConfigurable != configurable;
      }
    });
  }

  private boolean isPopupOverEditor(Component component) {
    Window editor = UIUtil.getWindow(this);
    if (editor != null) {
      Window popup = UIUtil.getWindow(component);
      // light-weight popup is located on the layered pane of the same window
      if (popup == editor) {
        return true;
      }
      // heavy-weight popup opens new window with the corresponding parent
      if (popup != null && editor == popup.getParent()) {
        if (popup instanceof JDialog) {
          JDialog dialog = (JDialog)popup;
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

  final boolean updateIfCurrent(Configurable configurable) {
    if (myConfigurable != configurable) {
      return false;
    }
    updateCurrent(configurable, false);
    return true;
  }

  final ActionCallback select(final Configurable configurable) {
    assert !myDisposed : "Already disposed";
    ActionCallback callback = myCardPanel.select(configurable, false);
    callback.doWhenDone(() -> {
      myConfigurable = configurable;
      updateCurrent(configurable, false);
      postUpdateCurrent(configurable);
      if (configurable != null) {
        FeatureUsageUiEvents.INSTANCE.logSelectConfigurable(getConfigurableEventId(configurable));
      }
    });
    return callback;
  }

  final boolean setError(ConfigurationException exception) {
    if (exception == null) {
      myErrorLabel.setVisible(false);
      return true;
    }
    if (myErrorLabel.isEnabled()) {
      myErrorLabel.setText("<html><body><strong>" + exception.getTitle() + "</strong>:<br>" + exception.getMessage());
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
    content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    String key = configurable == null ? null : ConfigurableVisitor.ByID.getID(configurable) + ".settings.description";
    String description = key == null ? null : getString(configurable, key);
    if (description == null) {
      description = "Select configuration element in the tree to edit its settings";
      content.add(BorderLayout.CENTER, new JLabel(description, SwingConstants.CENTER));
      content.setPreferredSize(JBUI.size(800, 600));
    }
    else {
      content.add(BorderLayout.NORTH, new JLabel(description));
      if (configurable instanceof Configurable.Composite) {
        Configurable.Composite composite = (Configurable.Composite)configurable;

        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        content.add(BorderLayout.CENTER, panel);
        panel.add(Box.createVerticalStrut(10));
        for (Configurable current : composite.getConfigurables()) {
          LinkLabel label = LinkLabel.create(current.getDisplayName(), () -> openLink(current));
          label.setBorder(BorderFactory.createEmptyBorder(1, 17, 3, 1));
          panel.add(label);
        }
      }
    }
    JScrollPane pane = createScrollPane(content, true);
    pane.setHorizontalScrollBarPolicy(HORIZONTAL_SCROLLBAR_NEVER);
    return pane;
  }

  private static String getString(Configurable configurable, String key) {
    JBIterable<Configurable> it = JBIterable.of(configurable).append(
      JBIterable.of(configurable instanceof Configurable.Composite ? ((Configurable.Composite)configurable).getConfigurables() : null));
    ResourceBundle bundle = ConfigurableExtensionPointUtil.getBundle(key, it, null);
    return bundle != null ? bundle.getString(key) : null;
  }

  static ConfigurationException apply(Configurable configurable) {
    if (configurable != null) {
      try {
        configurable.apply();
        final String key = getConfigurableEventId(configurable);
        FeatureUsageUiEvents.INSTANCE.logApplyConfigurable(key);
      }
      catch (ConfigurationException exception) {
        return exception;
      }
    }
    return null;
  }

  @NotNull
  private static String getConfigurableEventId(@NotNull Configurable configurable) {
    return "ide.settings." + ConvertUsagesUtil.escapeDescriptorName(StringUtil.notNullize(configurable.getDisplayName()));
  }
}
