/*
 * Copyright 2002-2005 Sascha Weinreuter
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
package org.intellij.plugins.xpathView.ui;

import com.intellij.openapi.diagnostic.Logger;
import org.intellij.plugins.xpathView.Config;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ConfigUI extends JPanel implements ActionListener {
    private static final Logger LOG = Logger.getInstance("org.intellij.plugins.xpathView.ui.ConfigUI");

    final JColorChooser chooser = new JColorChooser();

    private JCheckBox scrollToFirst;
    private JCheckBox useContextAtCursor;
    private JCheckBox highlightStartTagOnly;
    private JCheckBox addErrorStripe;
    private JCheckBox showInToolbar;
    private JCheckBox showInMainMenu;

    private JButton chooseHighlight;
    private JButton chooseContext;

    public ConfigUI(Config configuration) {
        init();
        setConfig(configuration);
    }

    private void init() {
        setLayout(new BorderLayout());
        JPanel c = this;

        scrollToFirst = new JCheckBox("Scroll first hit into visible area");
        scrollToFirst.setMnemonic('S');

        useContextAtCursor = new JCheckBox("Use node at cursor as context node");
        useContextAtCursor.setMnemonic('N');
        useContextAtCursor.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                stateChanged();
            }
        });

        highlightStartTagOnly = new JCheckBox("Highlight only start tag instead of whole tag content");
        highlightStartTagOnly.setMnemonic('H');

        addErrorStripe = new JCheckBox("Add error stripe markers for each result");
        addErrorStripe.setMnemonic('A');

        showInToolbar = new JCheckBox("Show actions in Toolbar");
        showInToolbar.setMnemonic('T');
        showInToolbar.setToolTipText("Uncheck to remove XPath-related actions from the toolbar");
        showInMainMenu = new JCheckBox("Show actions in Main Menu");
        showInMainMenu.setMnemonic('M');
        showInMainMenu.setToolTipText("Uncheck to remove XPath-related actions from the Main-Menubar");

        chooseHighlight = new JButton("Highlight color");
        chooseHighlight.setMnemonic('c');
        chooseHighlight.addActionListener(this);

        chooseContext = new JButton("Context node color");
        chooseContext.setMnemonic('n');
        chooseContext.addActionListener(this);

        JPanel settings = new JPanel(new BorderLayout());
        settings.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Settings"));
        c.add(c = new JPanel(new BorderLayout()), BorderLayout.NORTH);
        c.add(settings, BorderLayout.NORTH);

        settings.add(scrollToFirst, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(useContextAtCursor, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(highlightStartTagOnly, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(addErrorStripe, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(showInToolbar, BorderLayout.NORTH);
        settings.add(settings = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        settings.add(showInMainMenu, BorderLayout.NORTH);
        settings.add(/*settings = */new JPanel(new BorderLayout()), BorderLayout.SOUTH);

        JPanel colors = new JPanel(new BorderLayout());
        colors.setBorder(new TitledBorder(new EtchedBorder(EtchedBorder.LOWERED), "Colors"));
        c.add(c = new JPanel(new BorderLayout()), BorderLayout.SOUTH);
        c.add(colors, BorderLayout.NORTH);

        colors.add(chooseHighlight, BorderLayout.NORTH);
        colors.add(chooseContext, BorderLayout.SOUTH);
    }

    public Config getConfig() {
        final Config config = new Config();
        config.setHighlightStartTagOnly(highlightStartTagOnly.isSelected());
        config.setUseContextAtCursor(useContextAtCursor.isSelected());
        config.setScrollToFirst(scrollToFirst.isSelected());
        config.setAddErrorStripe(addErrorStripe.isSelected());
        config.SHOW_IN_TOOLBAR = showInToolbar.isSelected();
        config.SHOW_IN_MAIN_MENU = showInMainMenu.isSelected();
        config.getAttributes().setBackgroundColor(chooseHighlight.getBackground());
        if (useContextAtCursor.isSelected()) {
            config.getContextAttributes().setBackgroundColor(chooseContext.getBackground());
        }
        return config;
    }

    public void setConfig(Config configuration) {
        scrollToFirst.setSelected(configuration.isScrollToFirst());
        highlightStartTagOnly.setSelected(configuration.isHighlightStartTagOnly());
        useContextAtCursor.setSelected(configuration.isUseContextAtCursor());
        addErrorStripe.setSelected(configuration.isAddErrorStripe());
        showInToolbar.setSelected(configuration.SHOW_IN_TOOLBAR);
        showInMainMenu.setSelected(configuration.SHOW_IN_MAIN_MENU);
        chooseHighlight.setBackground(configuration.getAttributes().getBackgroundColor());
        chooseContext.setBackground(configuration.getContextAttributes().getBackgroundColor());
        stateChanged();
    }

    private void stateChanged() {
        chooseContext.setEnabled(useContextAtCursor.isSelected());
    }

    public void actionPerformed(ActionEvent e) {
        final Object source = e.getSource();
        LOG.assertTrue(source instanceof JButton);

        final JButton b = (JButton)source;
        chooser.setColor(b.getBackground());
        final JDialog dialog = JColorChooser.createDialog(ConfigUI.this, b.getText(), true, chooser, new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                // OK
                b.setBackground(chooser.getColor());
            }
        }, null);
        dialog.setVisible(true);
    }

    public static void main(String[] args) {
        JFrame test = new JFrame("Config test");
        test.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        final JPanel comp = new JPanel(new BorderLayout());
        comp.add(new ConfigUI(new Config()), BorderLayout.CENTER);
        test.getContentPane().add(comp);
        test.setSize(450, 450);
        test.setVisible(true);
    }
}
