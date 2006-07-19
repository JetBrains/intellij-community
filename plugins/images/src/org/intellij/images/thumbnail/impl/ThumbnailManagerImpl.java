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
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManager;
import com.intellij.openapi.project.ProjectManagerListener;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

/**
 * Thumbail manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ThumbnailManagerImpl extends ThumbnailManager implements ApplicationComponent, ProjectManagerListener {
    @NonNls private static final String NAME = "Images.ThumbnailManager";
    private final ThumbnailSelectInTarget selectInTarget = new ThumbnailSelectInTarget();

    /**
     * Thumbnails per project
     */
    private static final Map<Project, ThumbnailView> views = new HashMap<Project, ThumbnailView>();

    @NotNull
    public ThumbnailView getThumbnailView(@NotNull Project project) {
        ThumbnailView thumbnailView = views.get(project);
        if (thumbnailView == null) {
            thumbnailView = new ThumbnailViewImpl(project);
            views.put(project, thumbnailView);
        }
        return thumbnailView;
    }

    @NotNull
    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
        ProjectManager.getInstance().addProjectManagerListener(this);
    }

    public void disposeComponent() {
        ProjectManager.getInstance().removeProjectManagerListener(this);
    }

    public void projectOpened(Project project) {
        SelectInManager selectInManager = SelectInManager.getInstance(project);
        selectInManager.addTarget(selectInTarget);
    }

    public boolean canCloseProject(Project project) {
        return true;
    }

    public void projectClosed(Project project) {
        ThumbnailView thumbnailView = views.remove(project);
        if (thumbnailView != null) {
            thumbnailView.dispose();
        }
        SelectInManager selectInManager = SelectInManager.getInstance(project);
        selectInManager.removeTarget(selectInTarget);
    }

    public void projectClosing(Project project) {
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
                ThumbnailView thumbnailView = getThumbnailView(context.getProject());
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
