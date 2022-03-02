// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.configuration.ui;

import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.registry.Registry;
import com.intellij.util.ui.AsyncProcessIcon;
import org.jetbrains.annotations.Nls;
import org.jetbrains.kotlin.idea.KotlinBundle;
import org.jetbrains.kotlin.idea.KotlinPluginUtil;
import org.jetbrains.kotlin.idea.configuration.ExperimentalFeaturesPanel;

import javax.swing.*;
import java.util.List;

public class KotlinLanguageConfigurationForm {
    public JComboBox<String> channelCombo;
    public JButton reCheckButton;
    public JPanel mainPanel;
    public AsyncProcessIcon updateCheckProgressIcon;
    public JLabel updateStatusLabel;
    public JButton installButton;
    public JLabel installStatusLabel;
    private JLabel verifierDisabledText;
    private JPanel pluginVersionPanel;
    private JTextPane currentVersion;
    public ExperimentalFeaturesPanel experimentalFeaturesPanel;
    private JPanel experimentalFeaturesPanelContainer;

    public KotlinLanguageConfigurationForm() {
        showVerifierDisabledStatus();
        experimentalFeaturesPanelContainer.setVisible(ExperimentalFeaturesPanel.Companion.shouldBeShown());
        @NlsSafe
        String pluginVersion = KotlinPluginUtil.getPluginVersion();

        if (KotlinPluginUtil.isPatched()) {
            @SuppressWarnings("deprecation")
            String pluginVersionFromIdea = KotlinPluginUtil.getPluginVersionFromIdea();
            currentVersion.setText(KotlinBundle.message("configuration.text.patched.original", pluginVersion, pluginVersionFromIdea));
        } else {
            currentVersion.setText(pluginVersion);
        }

        currentVersion.setBackground(pluginVersionPanel.getBackground());
    }

    public void initChannels(List<@NlsSafe String> channels) {
        channelCombo.removeAllItems();
        for (@NlsSafe String channel : channels) {
            channelCombo.addItem(channel);
        }

        int size = channelCombo.getModel().getSize();
        String maxLengthItem = "";
        for (int i = 0; i < size; i++) {
            String item = channelCombo.getModel().getElementAt(i);
            if (item.length() > maxLengthItem.length()) {
                maxLengthItem = item;
            }
        }
        channelCombo.setPrototypeDisplayValue(maxLengthItem + " ");
    }

    private void createUIComponents() {
        updateCheckProgressIcon = new AsyncProcessIcon("Plugin update check progress");
    }

    public void resetUpdateStatus() {
        updateStatusLabel.setText(" ");
        installButton.setVisible(false);
        installStatusLabel.setVisible(false);
    }

    public void setUpdateStatus(@Nls String message, boolean showInstallButton) {
        installButton.setEnabled(true);
        installButton.setVisible(showInstallButton);

        updateStatusLabel.setText(message);

        installStatusLabel.setVisible(true);
        installStatusLabel.setText("");
    }

    public void showInstallButton() {
        installButton.setEnabled(true);
        installButton.setVisible(true);
    }

    public void hideInstallButton() {
        installButton.setEnabled(false);
        installButton.setVisible(false);
    }

    private void showVerifierDisabledStatus() {
        //noinspection UnresolvedPluginConfigReference
        if (!Registry.is("kotlin.plugin.update.verifier.enabled", true)) {
            verifierDisabledText.setText(KotlinBundle.message("configuration.message.verifier.disabled"));
        }
        else {
            verifierDisabledText.setText("");
        }
    }
}
