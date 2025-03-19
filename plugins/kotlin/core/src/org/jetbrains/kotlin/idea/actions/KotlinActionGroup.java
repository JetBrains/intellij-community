// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.kotlin.idea.actions;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import org.jetbrains.annotations.NotNull;

public class KotlinActionGroup extends DefaultActionGroup implements DumbAware {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(AnActionEvent event) {
        Presentation p = event.getPresentation();
        boolean hasProject = event.getData(CommonDataKeys.PROJECT) != null;

        p.setVisible(hasProject);
    }
}
