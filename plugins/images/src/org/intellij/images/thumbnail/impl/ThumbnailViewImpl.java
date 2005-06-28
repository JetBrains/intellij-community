/** $Id$ */
package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowAnchor;
import com.intellij.openapi.wm.ToolWindowManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * Thumbnail view.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ThumbnailViewImpl implements ThumbnailView {
    private static final Icon TOOL_WINDOW_ICON = IconLoader.getIcon("/org/intellij/images/thumbnail/icons/ThumbnailToolWindow.png");

    private final Project project;
    private final ToolWindow toolWindow;

    private boolean recursive = false;
    private VirtualFile root = null;


    public ThumbnailViewImpl(Project project) {
        this.project = project;

        ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        toolWindow = windowManager.registerToolWindow(TOOLWINDOW_ID, new ThumbnailViewUI(this), ToolWindowAnchor.BOTTOM);
        toolWindow.setIcon(TOOL_WINDOW_ICON);
    }

    public void setRoot(@NotNull VirtualFile root) {
        this.root = root;
        updateUI();
    }

    public VirtualFile getRoot() {
        return root;
    }

    public boolean isRecursive() {
        return recursive;
    }

    public void setRecursive(boolean recursive) {
        this.recursive = recursive;
        updateUI();
    }

    private void updateUI() {
        ((ThumbnailViewUI)toolWindow.getComponent()).refresh();
    }

    public void show() {
        ((ThumbnailViewUI)toolWindow.getComponent()).createUI();

        toolWindow.setAvailable(true, null);
        toolWindow.activate(null);
    }

    public void hide() {
        toolWindow.setAvailable(false, null);
    }

    public Project getProject() {
        return project;
    }

    public void dispose() {
        ToolWindowManager windowManager = ToolWindowManager.getInstance(project);
        windowManager.unregisterToolWindow(TOOLWINDOW_ID);
    }


}
