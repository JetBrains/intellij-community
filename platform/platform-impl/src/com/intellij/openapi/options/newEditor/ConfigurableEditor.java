/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.options.newEditor;

import com.intellij.AbstractBundle;
import com.intellij.CommonBundle;
import com.intellij.internal.statistic.UsageTrigger;
import com.intellij.internal.statistic.beans.ConvertUsagesUtil;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.ex.AnActionListener;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.options.*;
import com.intellij.openapi.options.ex.ConfigurableVisitor;
import com.intellij.openapi.options.ex.ConfigurableWrapper;
import com.intellij.openapi.util.ActionCallback;
import com.intellij.ui.JBColor;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.labels.LinkLabel;
import com.intellij.util.ui.update.MergingUpdateQueue;
import com.intellij.util.ui.update.Update;

import javax.swing.*;
import java.awt.*;
import java.awt.event.AWTEventListener;
import java.awt.event.ActionEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.util.IdentityHashMap;
import java.util.ResourceBundle;

import static java.awt.Toolkit.getDefaultToolkit;
import static javax.swing.SwingUtilities.isDescendingFrom;

/**
 * @author Sergey.Malenkov
 */
class ConfigurableEditor extends AbstractEditor implements AnActionListener, AWTEventListener {
  private static final JBColor ERROR_BACKGROUND = new JBColor(0xffbfbf, 0x591f1f);
  private static final String RESET_NAME = "Reset";
  private static final String RESET_DESCRIPTION = "Rollback changes for this configuration element";
  private final MergingUpdateQueue myQueue = new MergingUpdateQueue("SettingsModification", 1000, false, this, this, this);
  private final IdentityHashMap<Configurable, JComponent> myConfigurableContent = new IdentityHashMap<Configurable, JComponent>();
  private final JLabel myErrorLabel = new JLabel();
  private final AbstractAction myApplyAction;
  private final AbstractAction myResetAction = new AbstractAction(RESET_NAME) {
    @Override
    public void actionPerformed(ActionEvent event) {
      if (myConfigurable != null) {
        myConfigurable.reset();
        updateCurrent(myConfigurable, true);
      }
    }
  };
  private Configurable myConfigurable;

  ConfigurableEditor(Disposable parent, Configurable configurable, boolean showApplyButton) {
    super(parent);
    myApplyAction = !showApplyButton ? null : new AbstractAction(CommonBundle.getApplyButtonText()) {
      @Override
      public void actionPerformed(ActionEvent event) {
        apply();
      }
    };
    myResetAction.putValue(Action.SHORT_DESCRIPTION, RESET_DESCRIPTION);
    myResetAction.setEnabled(false);
    myErrorLabel.setOpaque(true);
    myErrorLabel.setVisible(false);
    myErrorLabel.setVerticalTextPosition(SwingConstants.TOP);
    myErrorLabel.setBorder(BorderFactory.createEmptyBorder(10, 15, 15, 15));
    myErrorLabel.setBackground(ERROR_BACKGROUND);
    add(BorderLayout.SOUTH, myErrorLabel);
    ActionManager.getInstance().addAnActionListener(this, this);
    getDefaultToolkit().addAWTEventListener(this, AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
    myConfigurable = configurable;
    setCurrent(configurable, readContent(configurable));
  }

  @Override
  void disposeOnce() {
    getDefaultToolkit().removeAWTEventListener(this);
    for (Configurable configurable : myConfigurableContent.keySet()) {
      if (configurable != null) {
        configurable.disposeUIResources();
      }
    }
    myConfigurableContent.clear();
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
    return setError(apply(myConfigurable));
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
  public final void eventDispatched(AWTEvent event) {
    switch (event.getID()) {
      case MouseEvent.MOUSE_PRESSED:
      case MouseEvent.MOUSE_RELEASED:
      case MouseEvent.MOUSE_DRAGGED:
        MouseEvent me = (MouseEvent)event;
        if (isDescendingFrom(me.getComponent(), this)) {
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

  private void requestUpdate() {
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

  void updateCurrent(Configurable configurable, boolean reset) {
    boolean modified = configurable != null && configurable.isModified();
    if (myApplyAction != null) {
      myApplyAction.setEnabled(modified);
    }
    myResetAction.setEnabled(modified);
    if (!modified && reset) {
      setError(null);
    }
  }

  private void setCurrent(Configurable configurable, JComponent content) {
    if (this != content.getParent()) {
      removeAll();
      add(BorderLayout.CENTER, content);
      add(BorderLayout.SOUTH, myErrorLabel);
      revalidate();
      repaint();
    }
    updateCurrent(configurable, false);
  }

  final boolean updateIfCurrent(Configurable configurable) {
    if (myConfigurable != configurable) {
      return false;
    }
    updateCurrent(configurable, false);
    return true;
  }

  final ActionCallback select(final Configurable configurable) {
    myConfigurable = configurable;
    JComponent content = myConfigurableContent.get(configurable);
    if (content != null) {
      setCurrent(configurable, content);
      return ActionCallback.DONE;
    }
    final ActionCallback callback = new ActionCallback();
    ApplicationManager.getApplication().invokeLater(new Runnable() {
      @Override
      public void run() {
        if (!myDisposed && myConfigurable == configurable) {
          JComponent content = readContent(configurable);
          myConfigurableContent.put(configurable, content);
          setCurrent(configurable, content);
          callback.setDone();
        }
        else {
          callback.setRejected();
        }
      }
    }, ModalityState.any());
    return callback;
  }

  final boolean setError(ConfigurationException exception) {
    if (exception == null) {
      myErrorLabel.setVisible(false);
      return true;
    }
    Font font = myErrorLabel.getFont();
    if (font != null) {
      myErrorLabel.setFont(font.deriveFont(2f + font.getSize()));
    }
    myErrorLabel.setText("<html><body><strong>Changes were not applied because of the following error</strong>:<br>" + exception.getMessage());
    myErrorLabel.setVisible(true);
    return false;
  }

  final JComponent getContent(Configurable configurable) {
    return myConfigurableContent.get(configurable);
  }

  final JComponent readContent(final Configurable configurable) {
    JComponent content = getContent(configurable);
    if (content != null) {
      return content;
    }
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        myConfigurableContent.put(configurable, createContent(configurable));
      }
    });
    return getContent(configurable);
  }

  private JComponent createContent(Configurable configurable) {
    JComponent content = configurable == null ? null : configurable.createComponent();
    if (content != null) {
      configurable.reset();
      if (ConfigurableWrapper.cast(MasterDetails.class, configurable) == null) {
        if (ConfigurableWrapper.cast(Configurable.NoMargin.class, configurable) == null) {
          content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        }
        if (ConfigurableWrapper.cast(Configurable.NoScroll.class, configurable) == null) {
          JScrollPane scroll = ScrollPaneFactory.createScrollPane(content, true);
          scroll.getVerticalScrollBar().setUnitIncrement(10);
          content = scroll;
        }
      }
    }
    else {
      content = new JPanel(new BorderLayout());
      content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
      String key = configurable == null ? null : ConfigurableVisitor.ByID.getID(configurable) + ".settings.description";
      String description = key == null ? null : getString(configurable, key);
      if (description == null) {
        description = "Select configuration element in the tree to edit its settings";
        content.add(BorderLayout.CENTER, new JLabel(description, SwingConstants.CENTER));
        content.setPreferredSize(new Dimension(800, 600));
      }
      else {
        content.add(BorderLayout.NORTH, new JLabel(description));
        if (configurable instanceof Configurable.Composite) {
          Configurable.Composite composite = (Configurable.Composite)configurable;

          JPanel panel = new JPanel();
          panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
          content.add(BorderLayout.CENTER, panel);
          panel.add(Box.createVerticalStrut(10));
          for (final Configurable current : composite.getConfigurables()) {
            LinkLabel label = new LinkLabel(current.getDisplayName(), null) {
              @Override
              public void doClick() {
                openLink(current);
              }
            };
            label.setBorder(BorderFactory.createEmptyBorder(1, 17, 1, 1));
            panel.add(label);
          }
        }
      }
    }
    return content;
  }

  private static String getString(Configurable configurable, String key) {
    try {
      if (configurable instanceof ConfigurableWrapper) {
        ConfigurableWrapper wrapper = (ConfigurableWrapper)configurable;
        ConfigurableEP ep = wrapper.getExtensionPoint();
        ResourceBundle bundle = AbstractBundle.getResourceBundle(ep.bundle, ep.getPluginDescriptor().getPluginClassLoader());
        return CommonBundle.message(bundle, key);
      }
      return OptionsBundle.message(key);
    }
    catch (AssertionError error) {
      return null;
    }
  }

  static ConfigurationException apply(Configurable configurable) {
    if (configurable != null) {
      try {
        configurable.apply();
        UsageTrigger.trigger("ide.settings." + ConvertUsagesUtil.escapeDescriptorName(configurable.getDisplayName()));
      }
      catch (ConfigurationException exception) {
        return exception;
      }
    }
    return null;
  }
}
