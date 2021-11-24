// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.idea.refactoring.ui;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtilCore;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.refactoring.move.moveClassesOrPackages.DestinationFolderComboBox;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.kotlin.idea.roots.ProjectRootUtilsKt;

import java.util.List;

import static org.jetbrains.kotlin.idea.roots.ProjectRootUtilsKt.getKotlinAwareDestinationSourceRoots;

public abstract class KotlinDestinationFolderComboBox extends DestinationFolderComboBox {

    protected boolean sourceRootsInTargetDirOnly() {
        return false;
    }

    @Override
    protected @NotNull List<VirtualFile> getSourceRoots(Project project, PsiDirectory initialTargetDirectory) {
        if (sourceRootsInTargetDirOnly()) {
            Module module = ModuleUtilCore.findModuleForFile(initialTargetDirectory.getVirtualFile(), project);
            if (module != null) {
                return ProjectRootUtilsKt.collectKotlinAwareDestinationSourceRoots(module);
            }
        }
        return getKotlinAwareDestinationSourceRoots(project);
    }
}