package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.thumbnail.ThumbnailManager;
import org.intellij.images.thumbnail.ThumbnailView;

/**
 * Show thumbnail for directory.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class ShowThumbnailsAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
        if (project != null && file != null && file.isDirectory()) {
            ThumbnailManager thumbnailManager = ThumbnailManager.getInstance();
            ThumbnailView thumbnailView = thumbnailManager.getThumbnailView(project);
            thumbnailView.setRoot(file);
            thumbnailView.show();
        }
    }

    public void update(AnActionEvent e) {
        super.update(e);
        DataContext dataContext = e.getDataContext();
        VirtualFile file = (VirtualFile)dataContext.getData(DataConstants.VIRTUAL_FILE);
        e.getPresentation().setEnabled(file != null && file.isDirectory());
    }
}
