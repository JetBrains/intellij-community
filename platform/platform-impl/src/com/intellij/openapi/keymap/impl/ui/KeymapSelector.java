// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.application.options.schemes.AbstractSchemeActions;
import com.intellij.application.options.schemes.SchemesModel;
import com.intellij.application.options.schemes.SimpleSchemesPanel;
import com.intellij.ide.DataManager;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.keymap.KeyMapBundle;
import com.intellij.openapi.keymap.Keymap;
import com.intellij.openapi.keymap.KeymapManagerListener;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.options.ex.Settings;
import com.intellij.openapi.ui.MessageType;
import com.intellij.ui.components.ActionLink;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.function.Consumer;

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

  void attachKeymapListener(@NotNull Disposable parentDisposable) {
    ApplicationManager.getApplication().getMessageBus().connect(parentDisposable).subscribe(KeymapManagerListener.TOPIC, new KeymapManagerListener() {
      @Override
      public void keymapAdded(@NotNull Keymap keymap) {
        manager.handleKeymapAdded(keymap);
        resetSchemes(manager.getSchemes());
      }

      @Override
      public void keymapRemoved(@NotNull Keymap keymap) {
        manager.handleKeymapRemoved(keymap);
        resetSchemes(manager.getSchemes());
      }

      @Override
      public void activeKeymapChanged(@Nullable Keymap keymap) {
        manager.handleActiveKeymapChanged(keymap);
      }
    });
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
    ActionLink link = new ActionLink(
      KeyMapBundle.message("link.get.more.keymaps.in.0.plugins", ShowSettingsUtil.getSettingsMenuName()),
      e -> {
        Settings settings = Settings.KEY.getData(DataManager.getInstance().getDataContext((ActionLink)e.getSource()));
        if (settings != null) {
          settings.select(settings.find("preferences.pluginManager"), "/tag:Keymap");
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
