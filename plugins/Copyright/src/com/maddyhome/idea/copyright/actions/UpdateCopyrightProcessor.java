package com.maddyhome.idea.copyright.actions;

/*
 * Copyright - Copyright notice updater for IDEA
 * Copyright (C) 2004-2005 Rick Maddy. All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.psi.UpdateCopyright;
import com.maddyhome.idea.copyright.psi.UpdateCopyrightFactory;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

public class UpdateCopyrightProcessor extends AbstractFileProcessor
{
    public UpdateCopyrightProcessor(Project project, Module module)
    {
        super(project, module, TITLE, MESSAGE);
        setup(project, module);
    }

    public UpdateCopyrightProcessor(Project project, Module module, PsiDirectory dir, boolean subdirs)
    {
        super(project, dir, subdirs, TITLE, MESSAGE);
        setup(project, module);
    }

    public UpdateCopyrightProcessor(Project project, Module module, PsiFile file)
    {
        super(project, file, TITLE, MESSAGE);
        setup(project, module);
    }

    public UpdateCopyrightProcessor(Project project, Module module, PsiFile[] files)
    {
        super(project, files, TITLE, MESSAGE, null);
        setup(project, module);
    }

    protected Runnable preprocessFile(final PsiFile file) throws IncorrectOperationException
    {
        VirtualFile vfile = file.getVirtualFile();
        if (vfile == null) return EmptyRunnable.getInstance();
        final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
        if (progressIndicator != null) {
            progressIndicator.setText2(vfile.getPresentableUrl());
        }
        Module mod = module;
        if (module == null)
        {
            mod = ProjectRootManager.getInstance(project).getFileIndex().getModuleForFile(vfile);
        }

        if (mod == null) return EmptyRunnable.getInstance();

        Options opts = CopyrightManager.getInstance(project).getCopyrightOptions(file);

        if (opts != null && FileTypeUtil.getInstance().isSupportedFile(file))
        {
            logger.debug("process " + file);
            final UpdateCopyright update = UpdateCopyrightFactory.createUpdateCopyright(project, mod, file, opts);
            if (update != null)
            {
                update.prepare();
            }

            return new Runnable() {
                public void run()
                {
                    try
                    {
                        if (update != null)
                        {
                            update.complete();
                        }
                    }
                    catch (IncorrectOperationException e)
                    {
                        logger.error(e);
                    }
                    catch (Exception e)
                    {
                        logger.error(e);
                    }
                }
            };
        }
        else
        {
            return EmptyRunnable.getInstance();
        }
    }

    private void setup(Project project, Module module)
    {
        this.project = project;
        this.module = module;

    }

    private Project project;
    private Module module;

    private static final String TITLE = "Update Copyright";
    private static final String MESSAGE = "Updating copyrights...";

    private static Logger logger = Logger.getInstance(UpdateCopyrightProcessor.class.getName());
}
