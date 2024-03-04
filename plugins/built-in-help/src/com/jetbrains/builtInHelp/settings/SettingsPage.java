// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.jetbrains.builtInHelp.settings;

import com.intellij.ide.IdeBundle;
import com.intellij.ide.browsers.WebBrowser;
import com.intellij.ide.browsers.WebBrowserManager;
import com.intellij.openapi.options.Configurable;
import com.jetbrains.builtInHelp.BuiltInHelpBundle;


import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;


public class SettingsPage implements Configurable {

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

      final HelpPluginSettings settings = HelpPluginSettings.Companion.getInstance();

      settings.setOpenHelpBaseUrl(baseUrl.getText());
      settings.setUseBrowser(String.valueOf(webBrowserList.getSelectedItem()));
      settings.setOpenHelpFromWeb(openWebSite.isSelected());

      modified = false;
    }

    void reset() {

      final HelpPluginSettings settings = HelpPluginSettings.Companion.getInstance();

      final String storedSelection = settings.getUseBrowser();
      int totalItems = webBrowserList.getItemCount();

      for (int i = 0; i < totalItems; i++) {
        if (webBrowserList.getItemAt(i).equals(storedSelection)) {
          webBrowserList.setSelectedIndex(i);
          break;
        }
      }

      openWebSite.setSelected(settings.getOpenHelpFromWeb());
      baseUrl.setText(settings.getOpenHelpBaseUrl());
      modified = false;
    }
  }
}
