/** $Id$ */
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

/**
 * Hide tool window.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
final public class HideThumbnailsAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project != null) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getInstance();
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView(project);
            thumbnailView.hide();
        }
    }
}
