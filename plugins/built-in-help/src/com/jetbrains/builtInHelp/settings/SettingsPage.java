// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.settings;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.options.Configurable;
import com.intellij.openapi.util.Pair;
import com.jetbrains.builtInHelp.BuiltInHelpBundle;
import com.jetbrains.builtInHelp.Utils;
import org.jetbrains.annotations.NonNls;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class SettingsPage implements Configurable {

  @NonNls
  public static final SettingKey OPEN_HELP_FROM_WEB = SettingKey.simple("bundled.help.open.web.site.when.possible");
  @NonNls
  public static final SettingKey USE_BROWSER = SettingKey.simple("bundled.help.use.specific.web.browser");
  @NonNls
  public static final SettingKey OPEN_HELP_BASE_URL = SettingKey.simple("bundled.help.open.web.site.base.url");

  private final SettingsPageUI ui = new SettingsPageUI();

  @Override
  public String getDisplayName() {
    return IdeBundle.message("configurable.SettingsPage.display.name");
  }

  @Override
  public JComponent createComponent() {
    ui.addListeners();

    WebBrowserManager mgr = WebBrowserManager.getInstance();
    ui.webBrowserList.addItem(BuiltInHelpBundle.message("use.default.browser"));
    for (WebBrowser browser : mgr.getBrowsers()) {
      ui.webBrowserList.addItem(browser.getName());
    }

    return ui.root;
  }

  @Override
  public boolean isModified() {
    return ui.modified;
  }

  @Override
  public void disposeUIResources() {
    ui.removeListeners();
  }

  @Override
  public void reset() {
    ui.reset();
  }

  @Override
  public void apply() {
    ui.apply();
  }

  public static class SettingKey extends Pair<String, Boolean> {

    SettingKey(String first, Boolean second) {
      super(first, second);
    }

    static SettingKey simple(String name) {
      return new SettingKey(name, false);
    }

    static SettingKey secure(String name) {
      return new SettingKey(name, true);
    }
  }

  static class SettingsPageUI {
    boolean modified = false;
    ActionListener actionListener = new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
        modified = true;
      }
    };

    JPanel root;
    JCheckBox openWebSite;
    JTextField baseUrl;
    JComboBox<String> webBrowserList;

    DocumentListener textChangeListener = new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        modified = true;
      }

      @Override
      public void removeUpdate(DocumentEvent e) {
        modified = true;
      }

      @Override
      public void changedUpdate(DocumentEvent e) {
        modified = true;
      }
    };

    void addListeners() {
      openWebSite.addActionListener(actionListener);
      webBrowserList.addActionListener(actionListener);
      baseUrl.getDocument().addDocumentListener(textChangeListener);
      baseUrl.addActionListener(actionListener);
    }

    void removeListeners() {
      openWebSite.removeActionListener(actionListener);
      webBrowserList.removeActionListener(actionListener);
      baseUrl.getDocument().removeDocumentListener(textChangeListener);
    }

    void apply() {
      Utils.setStoredValue(OPEN_HELP_FROM_WEB, String.valueOf(openWebSite.isSelected()));
      Utils.setStoredValue(USE_BROWSER, String.valueOf(webBrowserList.getSelectedItem()));
      Utils.setStoredValue(OPEN_HELP_BASE_URL, baseUrl.getText());
      modified = false;
    }

    void reset() {
      final String storedSelection = Utils.getStoredValue(USE_BROWSER, BuiltInHelpBundle.message("use.default.browser"));
      int totalItems = webBrowserList.getItemCount();

      for (int i = 0; i < totalItems; i++) {
        if (webBrowserList.getItemAt(i).equals(storedSelection)) {
          webBrowserList.setSelectedIndex(i);
          break;
        }
      }

      openWebSite.setSelected(Boolean.parseBoolean(Utils.getStoredValue(OPEN_HELP_FROM_WEB, "true")));
      baseUrl.setText(Utils.getStoredValue(OPEN_HELP_BASE_URL, Utils.BASE_HELP_URL));
      modified = false;
    }
  }
}
