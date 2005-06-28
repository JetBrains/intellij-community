package org.intellij.images.thumbnail.impl;

import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.project.ProjectManagerListener;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Thumbail manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final class ThumbnailManagerImpl extends ThumbnailManager implements ApplicationComponent, ProjectManagerListener {
    private static final String NAME = "Images.ThumbnailManager";

    /**
     * Thumbnails per project
     */
    private static final Map<Project, ThumbnailView> views = new WeakHashMap<Project, ThumbnailView>();

    @NotNull
    public ThumbnailView getThumbnailView(@NotNull Project project) {
        ThumbnailView thumbnailView = views.get(project);
        if (thumbnailView == null) {
            thumbnailView = new ThumbnailViewImpl(project);
            views.put(project, thumbnailView);
        }
        return thumbnailView;
    }

    public String getComponentName() {
        return NAME;
    }

    public void initComponent() {
    }

    public void disposeComponent() {
    }

    public void projectOpened(Project project) {
    }

    public boolean canCloseProject(Project project) {
        return false;
    }

    public void projectClosed(Project project) {
        views.remove(project);
    }

    public void projectClosing(Project project) {
    }

}
