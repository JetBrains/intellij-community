/** $Id$ */
package org.intellij.images.thumbnail.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

/**
 * Level up to browse images.
 *
 * @author <a href="aefimov@tengry.com">Alexey Efimov</a>
 */
public final class UpFolderAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project != null) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getInstance();
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView(project);
            VirtualFile root = thumbnailView.getRoot();
            if (root != null) {
                VirtualFile parent = root.getParent();
                if (parent != null) {
                    thumbnailView.setRoot(parent);
                }
            }
        }
    }

    public void update(AnActionEvent e) {
        super.update(e);
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        if (project != null) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getInstance();
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView(project);
            VirtualFile root = thumbnailView.getRoot();
            e.getPresentation().setEnabled(root != null ? root.getParent() != null && !thumbnailView.isRecursive() : false);
        }
    }
}
