// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.testGuiFramework.recorder.ui;

import com.intellij.openapi.actionSystem.ActionManager;
import com.intellij.openapi.actionSystem.ActionToolbar;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.testGuiFramework.recorder.actions.PerformScriptAction;
import com.intellij.testGuiFramework.recorder.actions.StartPauseRecAction;
import com.intellij.testGuiFramework.recorder.actions.StopRecAction;
import com.intellij.util.ui.AsyncProcessIcon;
import com.intellij.util.ui.JBFont;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

/**
 * @author Sergey Karashevich
 */
public class GuiScriptEditorPanel {
    private JPanel editorPanel;
    private JPanel myPanel;
    private JLabel myStatusLabel;
    private AsyncProcessIcon progressIcon;
    private JPanel iconButtonRow;

    public static final String GUI_SCRIPT_EDITOR_PLACE = "GUI_SCRIPT_EDITOR_PLACE";

    private final GuiScriptEditor myEditor;

    public GuiScriptEditorPanel() {
        super();
        myStatusLabel.setFont(SystemInfo.isMac ? JBUI.Fonts.label(11) : JBFont.label());
        progressIcon.setVisible(false);

        myEditor = new GuiScriptEditor();
        progressIcon.suspend();
        editorPanel.removeAll();
        editorPanel.add(myEditor.getPanel(), BorderLayout.CENTER);

        installActionToolbar();
    }

    public @NotNull Editor getEditor(){
        return myEditor.getMyEditor();
    }

    private void installActionToolbar() {
        final DefaultActionGroup group = new DefaultActionGroup();
        group.add(new StartPauseRecAction());
        group.add(new StopRecAction());
        group.add(new PerformScriptAction());
        final ActionToolbar toolbar = ActionManager.getInstance().createActionToolbar(GUI_SCRIPT_EDITOR_PLACE, group, true);

        iconButtonRow.add(toolbar.getComponent(), BorderLayout.CENTER);
    }

    public Component getPanel(){
        return myPanel;
    }

    public void updateStatus(String status){
        myStatusLabel.setText(status);
        myStatusLabel.repaint();
    }


    public void updateStatusWithProgress(String statusWithProgress){
        progressIcon.setVisible(true);
        progressIcon.resume();
        myStatusLabel.setText(statusWithProgress);
        myStatusLabel.repaint();
    }

    public void stopProgress(){
        progressIcon.setVisible(false);
        progressIcon.suspend();
    }

    private void createUIComponents() {
        progressIcon = new AsyncProcessIcon("Progress");
    }
}
