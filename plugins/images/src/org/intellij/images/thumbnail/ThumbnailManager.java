package org.intellij.images.thumbnail;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Thumbnail manager.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public abstract class ThumbnailManager {
    public static ThumbnailManager getInstance() {
        Application application = ApplicationManager.getApplication();
        return application.getComponent(ThumbnailManager.class);
    }

    /**
     * Create thumbnail view
     */
    public abstract @NotNull ThumbnailView getThumbnailView(@NotNull Project project);
}
