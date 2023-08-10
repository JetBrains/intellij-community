// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.conversion.copy;

import com.intellij.CommonBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;
import org.jetbrains.kotlin.idea.editor.KotlinEditorOptions;

import javax.swing.*;
import java.awt.*;

@SuppressWarnings("UnusedDeclaration")
public class KotlinPasteFromJavaDialog extends DialogWrapper {
    private JPanel panel;
    private JCheckBox donTShowThisCheckBox;
    private JLabel questionLabel;
    private JButton buttonOK;

    public KotlinPasteFromJavaDialog(@NotNull Project project, boolean isPlainText) {
        super(project, true);
        setModal(true);
        getRootPane().setDefaultButton(buttonOK);
        setTitle(KotlinBundle.message("copy.title.convert.code.from.java"));
        if (isPlainText) {
            questionLabel.setText(
                    KotlinBundle.message("copy.text.clipboard.content.seems.to.be.java.code.do.you.want.to.convert.it.to.kotlin"));
            //TODO: should we also use different set of settings?
        }
        init();
    }

    @Override
    protected JComponent createCenterPanel() {
        return panel;
    }

    @Override
    public Container getContentPane() {
        return panel;
    }

    @NotNull
    @Override
    protected Action @NotNull [] createActions() {
        setOKButtonText(CommonBundle.getYesButtonText());
        setCancelButtonText(CommonBundle.getNoButtonText());
        return new Action[] {getOKAction(), getCancelAction()};
    }

    @Override
    protected void doOKAction() {
        if (donTShowThisCheckBox.isSelected()) {
            KotlinEditorOptions.getInstance().setDonTShowConversionDialog(true);
        }
        super.doOKAction();
    }

    @Override
    public void dispose() {
        super.dispose();
    }
}
