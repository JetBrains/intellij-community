/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package com.intellij.openapi.editor.impl.breakout;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.editor.impl.EditorImpl;

@SuppressWarnings("ComponentNotRegistered")
public class ToggleBreakoutModeAction extends ToggleAction {
    public ToggleBreakoutModeAction() {
        super("Breakout Mode");
        getTemplatePresentation().setDescription("Toggle breakout mode in current editor");
    }

    @Override
    public boolean isSelected(AnActionEvent e) {
        return BreakoutMode.getInstance().isEnabled(getEditor(e));
    }

    @Override
    public void setSelected(AnActionEvent e, boolean state) {
        BreakoutMode.getInstance().setEnabled(getEditor(e), state);
    }

    @Override
    public void update(AnActionEvent e) {
        super.update(e);
        e.getPresentation().setVisible(ApplicationManager.getApplication().isInternal() && getEditor(e) != null);
    }

    private static EditorImpl getEditor(AnActionEvent e) {
        Editor editor = PlatformDataKeys.EDITOR.getData(e.getDataContext());
        return editor instanceof EditorImpl ? (EditorImpl) editor : null;
    }
}
