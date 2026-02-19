// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.base.codeInsight.copyPaste;

import com.intellij.CommonBundle;
import com.intellij.java.JavaBundle;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.VerticalFlowLayout;
import com.intellij.psi.PsiClass;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBLabel;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.kotlin.idea.base.resources.KotlinBundle;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JList;
import javax.swing.JPanel;
import java.awt.BorderLayout;
import java.awt.Dimension;

import static com.intellij.util.ui.UIUtil.ComponentStyle.SMALL;
import static com.intellij.util.ui.UIUtil.FontColor.BRIGHTER;

//TODO: this is a copy of com.intellij.codeInsight.editorActions.RestoreReferencesDialog
// Cell renderer was replaced by custom implementation
public class RestoreReferencesDialog extends DialogWrapper {
    private final Object[] myNamedElements;
    private final boolean myInRemoveMode;
    private JList myList;
    private Object[] mySelectedElements = PsiClass.EMPTY_ARRAY;
    private boolean myContainsClassesOnly = true;

    public RestoreReferencesDialog(final Project project, final Object[] elements, final boolean inRemoveMode) {
        super(project, true);
        myNamedElements = elements;
        myInRemoveMode = inRemoveMode;
        for (Object element : elements) {
            if (!(element instanceof PsiClass)) {
                myContainsClassesOnly = false;
                break;
            }
        }
        if (myContainsClassesOnly) {
            setTitle(JavaBundle.message("dialog.import.on.paste.title"));
        } else {
            setTitle(JavaBundle.message("dialog.import.on.paste.title2"));
        }
        init();

        myList.setSelectionInterval(0, myNamedElements.length - 1);
    }

    public RestoreReferencesDialog(final Project project, final Object[] elements) {
        this(project, elements, false);
    }

    @Override
    protected void doOKAction() {
        mySelectedElements = myList.getSelectedValuesList().toArray(new Object[0]);
        super.doOKAction();
    }

    @Override
    protected JComponent createCenterPanel() {
        final JPanel panel = new JPanel(new BorderLayout(UIUtil.DEFAULT_HGAP, UIUtil.DEFAULT_VGAP));
        myList = new JBList(myNamedElements);
        myList.setCellRenderer(new KotlinImportListRenderer());
        panel.add(ScrollPaneFactory.createScrollPane(myList), BorderLayout.CENTER);

        String text;
        if (myInRemoveMode) {
            text = KotlinBundle.message("copy.paste.select.imports.to.remove.text");
        } else {
            text = myContainsClassesOnly ?
                   JavaBundle.message("dialog.paste.on.import.text") :
                   JavaBundle.message("dialog.paste.on.import.text2");
        }
        panel.add(new JBLabel(text, SMALL, BRIGHTER), BorderLayout.NORTH);

        final JPanel buttonPanel = new JPanel(new VerticalFlowLayout());
        final JButton okButton = new JButton(CommonBundle.getOkButtonText());
        getRootPane().setDefaultButton(okButton);
        buttonPanel.add(okButton);
        final JButton cancelButton = new JButton(CommonBundle.getCancelButtonText());
        buttonPanel.add(cancelButton);

        panel.setPreferredSize(new Dimension(500, 400));

        return panel;
    }


    @Override
    protected String getDimensionServiceKey() {
        return "#com.intellij.codeInsight.editorActions.RestoreReferencesDialog";
    }

    public Object[] getSelectedElements() {
        return mySelectedElements;
    }
}
