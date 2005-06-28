/** $Id$ */
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.ToggleAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.project.Project;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

/**
 * Toggle recursive flag.
 *
 * @author <a href="aefimov@tengry.com">Alexey Efimov</a>
 */
public final class ToggleRecursiveAction extends ToggleAction {
    public boolean isSelected(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project != null) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getInstance();
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView(project);
            return thumbnailView.isRecursive();
        }
        return false;
    }

    public void setSelected(AnActionEvent e, boolean state) {
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project != null) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getInstance();
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView(project);
            thumbnailView.setRecursive(state);
        }
    }
}
