/*
 * Copyright 2004-2005 Alexey Efimov
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
package org.intellij.images.thumbnail.impl;

import com.intellij.ide.SelectInContext;
import com.intellij.ide.SelectInManager;
import com.intellij.ide.SelectInTarget;
import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

/**
 * Thumbail manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ThumbnailManagerImpl implements ThumbnailManager, ProjectComponent {
    @NonNls
    private static final String NAME = "Images.ThumbnailManager";
    private final ThumbnailSelectInTarget selectInTarget = new ThumbnailSelectInTarget();
    private final Project project;
    private ThumbnailView thumbnailView;


    public ThumbnailManagerImpl(Project project) {
        this.project = project;
    }

    @NotNull
    public final ThumbnailView getThumbnailView() {
        if (thumbnailView == null) {
            thumbnailView = new ThumbnailViewImpl(project);
        }
        return thumbnailView;
    }

    @NotNull
    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
        if (thumbnailView != null) {
            thumbnailView.dispose();
            thumbnailView = null;
        }
    }


    public void projectClosed() {
        SelectInManager selectInManager = SelectInManager.getInstance(project);
        selectInManager.removeTarget(selectInTarget);
    }

    public void projectOpened() {
        SelectInManager selectInManager = SelectInManager.getInstance(project);
        selectInManager.addTarget(selectInTarget);
    }

    private final class ThumbnailSelectInTarget implements SelectInTarget {
        public boolean canSelect(SelectInContext context) {
            VirtualFile virtualFile = context.getVirtualFile();
            return ImageFileTypeManager.getInstance().isImage(virtualFile) && virtualFile.getParent() != null;
        }

        public void selectIn(SelectInContext context, final boolean requestFocus) {
            VirtualFile virtualFile = context.getVirtualFile();
            VirtualFile parent = virtualFile.getParent();
            if (parent != null) {
                ThumbnailView thumbnailView = getThumbnailView();
                thumbnailView.setRoot(parent);
                thumbnailView.setVisible(true);
                thumbnailView.setSelected(virtualFile, true);
                thumbnailView.scrollToSelection();
            }
        }

        public String toString() {
            return getToolWindowId();
        }

        public String getToolWindowId() {
            return ThumbnailView.TOOLWINDOW_ID;
        }

        public String getMinorViewId() {
            return null;
        }

        public float getWeight() {
            return 10;
        }
    }
}
