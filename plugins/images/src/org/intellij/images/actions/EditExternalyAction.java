/** $Id$ */
package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.LocalFileSystem;
import org.intellij.images.fileTypes.ImageFileTypeManager;
import org.intellij.images.options.Options;
import org.intellij.images.options.OptionsManager;
import org.intellij.images.options.impl.OptionsConfigurabe;

import java.io.File;
import java.io.IOException;

/**
 * Open image file externaly.
 *
 * @author <a href="mailto:aefimov.box@gmail.com">Alexey Efimov</a>
 */
public final class EditExternalyAction extends AnAction {
    public void actionPerformed(AnActionEvent e) {
        DataContext dataContext = e.getDataContext();
        Project project = (Project)dataContext.getData(DataConstants.PROJECT);
        VirtualFile[] files = (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY);
        Options options = OptionsManager.getInstance().getOptions();
        String executablePath = options.getExternalEditorOptions().getExecutablePath();
        if (StringUtil.isEmpty(executablePath)) {
            Messages.showInfoMessage(project, "Please, configure external editor executable path", "External Editor not Configured");
            OptionsConfigurabe.show(project);
        } else {
            if (files != null) {
                ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
                StringBuffer commandLine = new StringBuffer(executablePath.replace('/', File.separatorChar));
                for (VirtualFile file : files) {
                    if ((file.getFileSystem() instanceof LocalFileSystem) && typeManager.isImage(file)) {
                        commandLine.append(" \"");
                        commandLine.append(VfsUtil.virtualToIoFile(file).getAbsolutePath());
                        commandLine.append('\"');
                    }
                }

                try {
                    File executableFile = new File(executablePath);
                    Runtime.getRuntime().exec(commandLine.toString(), null, executableFile.getParentFile());
                } catch (IOException ex) {
                    Messages.showErrorDialog(project, ex.getLocalizedMessage(), "Error opening executableFile");
                }
            }
        }
    }

    public void update(AnActionEvent e) {
        super.update(e);

        DataContext dataContext = e.getDataContext();
        VirtualFile[] files = (VirtualFile[])dataContext.getData(DataConstants.VIRTUAL_FILE_ARRAY);
        e.getPresentation().setEnabled(isImages(files));
    }

    private boolean isImages(VirtualFile[] files) {
        if (files != null) {
            ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
            for (VirtualFile file : files) {
                if (!(file.getFileSystem() instanceof LocalFileSystem) || !typeManager.isImage(file)) {
                    return false;
                }
            }
            return true;
        }
        return false;
    }
}
