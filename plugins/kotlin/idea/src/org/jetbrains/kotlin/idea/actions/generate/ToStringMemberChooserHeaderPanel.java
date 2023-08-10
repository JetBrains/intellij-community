// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.actions.generate;

import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.NlsSafe;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.java.generate.template.toString.ToStringTemplatesManager;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;

import javax.swing.*;
import java.awt.*;

public class ToStringMemberChooserHeaderPanel extends JPanel {
    private final JComboBox comboBox;
    private final JCheckBox generateSuperCheckBox;

    public ToStringMemberChooserHeaderPanel(boolean allowSuperCall) {
        super(new GridBagLayout());

        comboBox = new ComboBox(KotlinGenerateToStringAction.Generator.values());
        comboBox.setRenderer(
                new DefaultListCellRenderer() {
                    @NotNull
                    @Override
                    public Component getListCellRendererComponent(
                            JList list,
                            Object value,
                            int index,
                            boolean isSelected,
                            boolean cellHasFocus
                    ) {
                        super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                        @NlsSafe String text = ((KotlinGenerateToStringAction.Generator) value).getText();
                        setText(text);
                        return this;
                    }
                }
        );
        comboBox.setSelectedItem(ToStringTemplatesManager.getInstance().getDefaultTemplate());

        JLabel templatesLabel = new JLabel(KotlinBundle.message("action.generate.tostring.choose.implementation"));
        {
            String mnemonic = KotlinBundle.message("action.generate.tostring.choose.implementation.mnemonic");
            if (mnemonic.length() == 1) {
                templatesLabel.setDisplayedMnemonic(mnemonic.charAt(0));
            }
        }
        templatesLabel.setLabelFor(comboBox);

        GridBagConstraints constraints = new GridBagConstraints();
        constraints.anchor = GridBagConstraints.BASELINE;
        constraints.gridx = 0;
        add(templatesLabel, constraints);

        constraints.gridx = 1;
        constraints.weightx = 1.0;
        constraints.fill = GridBagConstraints.HORIZONTAL;
        add(comboBox, constraints);

        if (allowSuperCall) {
            generateSuperCheckBox = new JCheckBox(KotlinBundle.message("action.generate.tostring.generate.super.call"));
            String mnemonic = KotlinBundle.message("action.generate.tostring.generate.super.call.mnemonic");
            if (mnemonic.length() == 1) {
                generateSuperCheckBox.setMnemonic(mnemonic.charAt(0));
            }
            constraints.gridx = 2;
            constraints.weightx = 0.0;
            add(generateSuperCheckBox, constraints);
        }
        else {
            generateSuperCheckBox = null;
        }
    }

    public KotlinGenerateToStringAction.Generator getSelectedGenerator() {
        return (KotlinGenerateToStringAction.Generator) comboBox.getSelectedItem();
    }

    public boolean isGenerateSuperCall() {
        return generateSuperCheckBox != null && generateSuperCheckBox.isSelected();
    }
}