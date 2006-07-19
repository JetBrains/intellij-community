/** $Id$ */
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
package org.intellij.images.actions;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DataConstants;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.intellij.images.ImagesBundle;
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
            Messages.showErrorDialog(project,
                                     ImagesBundle.message("error.empty.external.editor.path"),
                                     ImagesBundle.message("error.title.empty.external.editor.path"));
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
                    Messages.showErrorDialog(project,
                                             ex.getLocalizedMessage(),
                                             ImagesBundle.message("error.title.launching.external.editor"));
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
        boolean isImagesFound = false;
        if (files != null) {
            ImageFileTypeManager typeManager = ImageFileTypeManager.getInstance();
            for (VirtualFile file : files) {
                boolean isImage = typeManager.isImage(file);
                isImagesFound |= isImage;
                if (!(file.getFileSystem() instanceof LocalFileSystem) || !isImage) {
                    return false;
                }
            }
        }
        return isImagesFound;
    }
}
