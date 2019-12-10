/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.application.options.schemes.SimpleSchemesPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.components.labels.SwingActionLink;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.util.function.Consumer;

/**
 * @author Sergey.Malenkov
 */
final class KeymapSelector extends SimpleSchemesPanel<KeymapScheme> {
  private KeymapSchemeManager manager;
  private final Consumer<? super Keymap> consumer;
  private String messageReplacement;
  private boolean messageShown;
  private boolean internal;

  KeymapSelector(Consumer<? super Keymap> consumer) {
    super(0);
    this.consumer = consumer;
  }

  @NotNull
  public KeymapSchemeManager getManager() {
    if (manager == null) manager = new KeymapSchemeManager(this);
    return manager;
  }

  @NotNull
  @Override
  public SchemesModel<KeymapScheme> getModel() {
    return getManager();
  }

  @Nullable
  @Override
  protected String getComboBoxLabel() {
    return null;
  }

  @NotNull
  @Override
  protected String getSchemeTypeName() {
    return "Keymap";
  }

  @NotNull
  @Override
  protected AbstractSchemeActions<KeymapScheme> createSchemeActions() {
    return getManager();
  }

  @Override
  protected int getIndent(@NotNull KeymapScheme scheme) {
    return scheme.isMutable() ? 1 : 0;
  }

  @Override
  protected boolean supportsProjectSchemes() {
    return false;
  }

  @Override
  protected boolean highlightNonDefaultSchemes() {
    return false;
  }

  @Override
  public boolean useBoldForNonRemovableSchemes() {
    return true;
  }

  @Override
  public void showMessage(@Nullable String message, @NotNull MessageType messageType) {
    messageShown = true;
    super.showMessage(message, messageType);
  }

  @Override
  public void clearMessage() {
    messageShown = false;
    super.showMessage(messageReplacement, MessageType.INFO);
  }

  @Override
  protected JComponent createTopComponent() {
    SwingActionLink link = new SwingActionLink(new AbstractAction("Get more keymaps in " + ShowSettingsUtil.getSettingsMenuName() + " | Plugins") {
      @Override
      public void actionPerformed(ActionEvent e) {
        Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext((SwingActionLink)e.getSource()));
        if (settings != null) {
          settings.select(settings.find("preferences.pluginManager"), "/tag:Keymap");
        }
      }
    });
    Box row = new Box(BoxLayout.X_AXIS);
    row.add(Box.createRigidArea(new JBDimension(2, 0)));
    row.add(link);
    row.add(Box.createHorizontalGlue());

    Box box = new Box(BoxLayout.Y_AXIS);
    box.add(Box.createRigidArea(new JBDimension(0, 5)));
    box.add(row);
    box.add(Box.createRigidArea(new JBDimension(0, 12)));
    return box;
  }

  void notifyConsumer(KeymapScheme scheme) {
    if (internal) return;

    Keymap keymap = scheme == null ? null : scheme.getParent();
    messageReplacement = keymap == null ? null : KeyMapBundle.message("based.on.keymap.label", keymap.getPresentableName());
    if (!messageShown) clearMessage();

    consumer.accept(scheme == null ? null : scheme.getCurrent());
  }

  void selectKeymap(KeymapScheme scheme, boolean reset) {
    try {
      internal = true;
      if (reset) resetSchemes(getManager().getSchemes());
      if (scheme != null) selectScheme(scheme);
    }
    finally {
      internal = false;
      notifyConsumer(getSelectedScheme());
    }
  }
}
