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

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.command.CommandProcessor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressWindow;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.EmptyRunnable;
import com.intellij.openapi.vfs.ReadonlyStatusHandler;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.util.IncorrectOperationException;
import com.maddyhome.idea.copyright.CopyrightManager;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class AbstractFileProcessor
{
    private final Project myProject;
    private final Module myModule;
    private PsiDirectory directory = null;
    private PsiFile file = null;
    private PsiFile[] files = null;
    private boolean subdirs = false;
    private final String message;
    private final String title;
    private final Runnable postProcess;

    protected abstract Runnable preprocessFile(PsiFile psifile) throws IncorrectOperationException;

    protected AbstractFileProcessor(Project project, String title, String message)
    {
        myProject = project;
        myModule = null;
        directory = null;
        subdirs = true;
        this.title = title;
        this.message = message;
        postProcess = null;
    }

    protected AbstractFileProcessor(Project project, Module module, String title, String message)
    {
        myProject = project;
        myModule = module;
        directory = null;
        subdirs = true;
        this.title = title;
        this.message = message;
        postProcess = null;
    }

    protected AbstractFileProcessor(Project project, PsiDirectory dir, boolean subdirs, String title, String message)
    {
        myProject = project;
        myModule = null;
        directory = dir;
        this.subdirs = subdirs;
        this.message = message;
        this.title = title;
        postProcess = null;
    }

    protected AbstractFileProcessor(Project project, PsiFile file, String title, String message)
    {
        myProject = project;
        myModule = null;
        this.file = file;
        this.message = message;
        this.title = title;
        postProcess = null;
    }

    protected AbstractFileProcessor(Project project, PsiFile[] files, String title, String message, Runnable runnable)
    {
        myProject = project;
        myModule = null;
        this.files = files;
        this.message = message;
        this.title = title;
        postProcess = runnable;
    }

    public void run()
    {
        if (directory != null)
        {
            process(directory, subdirs);
        }
        else if (files != null)
        {
            process(files);
        }
        else if (file != null)
        {
            process(file);
        }
        else if (myModule != null)
        {
            process(myModule);
        }
        else if (myProject != null)
        {
            process(myProject);
        }
    }

    private void process(final PsiFile file)
    {
        final VirtualFile virtualFile = file.getVirtualFile();
        assert virtualFile != null;
        if (!ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(virtualFile).hasReadonlyFiles()) {
           final Runnable[] resultRunnable = new Runnable[1];
            Runnable read = new Runnable()
            {
                public void run()
                {
                    try
                    {
                        resultRunnable[0] = preprocessFile(file);
                    }
                    catch (IncorrectOperationException incorrectoperationexception)
                    {
                        logger.error(incorrectoperationexception);
                    }
                }
            };
            if (resultRunnable[0] == null || resultRunnable[0].equals(EmptyRunnable.getInstance()))  {
                return;
            }
            Runnable write = new Runnable()
            {
                public void run()
                {
                    if (resultRunnable[0] != null)
                    {
                        resultRunnable[0].run();
                    }
                }
            };

            execute(read, write);
        }
    }


    private Runnable prepareFiles(List<PsiFile> files)
    {
        ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
        String msg = null;
        double fraction = 0.0D;
        if (indicator != null)
        {
            msg = indicator.getText();
            fraction = indicator.getFraction();
            indicator.setText(message);
        }

        final Runnable[] runnables = new Runnable[files.size()];
        for (int i = 0; i < files.size(); i++)
        {
            PsiFile pfile = files.get(i);
            if (pfile == null)
            {
                logger.debug("Unexpected null file at " + i);
                continue;
            }
            if (indicator != null)
            {
                if (indicator.isCanceled())
                {
                    return null;
                }

                indicator.setFraction((double)i / (double)files.size());
            }

            if (pfile.isWritable())
            {
                try
                {
                    runnables[i] = preprocessFile(pfile);
                }
                catch (IncorrectOperationException incorrectoperationexception)
                {
                    logger.error(incorrectoperationexception);
                }
            }

            files.set(i, null);
        }

        if (indicator != null)
        {
            indicator.setText(msg);
            indicator.setFraction(fraction);
        }

        return new Runnable()
        {
            public void run()
            {
                ProgressIndicator indicator = ProgressManager.getInstance().getProgressIndicator();
                String msg = null;
                double fraction = 0.0D;
                if (indicator != null)
                {
                    msg = indicator.getText();
                    fraction = indicator.getFraction();
                    indicator.setText(message);
                }

                for (int j = 0; j < runnables.length; j++)
                {
                    if (indicator != null)
                    {
                        if (indicator.isCanceled())
                        {
                            return;
                        }

                        indicator.setFraction((double)j / (double)runnables.length);
                    }

                    Runnable runnable = runnables[j];
                    if (runnable != null)
                    {
                        runnable.run();
                    }
                    runnables[j] = null;
                }

                if (indicator != null)
                {
                    indicator.setText(msg);
                    indicator.setFraction(fraction);
                }
            }
        };
    }

    private void process(final PsiFile[] files)
    {
        final Runnable[] resultRunnable = new Runnable[1];
        execute(new Runnable()
        {
            public void run()
            {
                resultRunnable[0] = prepareFiles(new ArrayList<PsiFile>(Arrays.asList(files)));
            }
        }, new Runnable()
        {
            public void run()
            {
                if (resultRunnable[0] != null)
                {
                    resultRunnable[0].run();
                }
            }
        });
    }

    private void process(PsiDirectory dir, boolean subdirs)
    {
        List<PsiFile> pfiles = new ArrayList<PsiFile>();
        findFiles(pfiles, dir, subdirs);
        handleFiles(pfiles);
    }

    private void process(Project project)
    {
        List<PsiFile> pfiles = new ArrayList<PsiFile>();
        findFiles(project, pfiles);
        handleFiles(pfiles);
    }

    private void process(Module module)
    {
        List<PsiFile> pfiles = new ArrayList<PsiFile>();
        findFiles(module, pfiles);
        handleFiles(pfiles);
    }

    private void findFiles(Project project, List<PsiFile> files)
    {
        Module[] modules = ModuleManager.getInstance(project).getModules();
        for (Module module : modules)
        {
            findFiles(module, files);
        }

    }

    private void findFiles(Module module, List<PsiFile> files)
    {
        VirtualFile[] roots = ModuleRootManager.getInstance(module).getContentRoots();
        for (VirtualFile file : roots)
        {
            PsiDirectory dir = PsiManager.getInstance(myProject).findDirectory(file);
            if (dir != null)
            {
                findFiles(files, dir, true);
            }
        }

    }

    private void handleFiles(final List<PsiFile> files)
    {
        final List<VirtualFile> vFiles = new ArrayList<VirtualFile>();
        for (PsiFile psiFile : files) {
            vFiles.add(psiFile.getVirtualFile());
        }
        if (!ReadonlyStatusHandler.getInstance(myProject).ensureFilesWritable(vFiles.toArray(new VirtualFile[vFiles.size()])).hasReadonlyFiles()) {
            if (!files.isEmpty())
            {
                final Runnable[] resultRunnable = new Runnable[1];
                execute(new Runnable()
                {
                    public void run()
                    {
                        resultRunnable[0] = prepareFiles(files);
                    }
                }, new Runnable()
                {
                    public void run()
                    {
                        if (resultRunnable[0] != null)
                        {
                            resultRunnable[0].run();
                        }
                    }
                });
            }
        }
    }

    private static void findFiles(List<PsiFile> files, PsiDirectory directory, boolean subdirs)
    {
        final Project project = directory.getProject();
        PsiFile[] locals = directory.getFiles();
        for (PsiFile local : locals)
        {
            Options opts = CopyrightManager.getInstance(project).getCopyrightOptions(local);
             if (opts != null && FileTypeUtil.getInstance().isSupportedFile(local)) {
                 files.add(local);
             }

        }

        if (subdirs)
        {
            PsiDirectory[] dirs = directory.getSubdirectories();
            for (PsiDirectory dir : dirs)
            {
                findFiles(files, dir, subdirs);
            }

        }
    }

    private void execute(final Runnable readAction, final Runnable writeAction)
    {
        final ProgressWindow progressWindow = new ProgressWindow(true, myProject);
        progressWindow.setTitle(title);
        progressWindow.setText(message);
        final ModalityState modalityState = ModalityState.current();
        final Runnable process = new Runnable()
        {
            public void run()
            {
                ApplicationManager.getApplication().runReadAction(readAction);
            }
        };

        Runnable runnable = new Runnable()
        {
            public void run()
            {
                try
                {
                    ProgressManager.getInstance().runProcess(process, progressWindow);
                }
                catch (ProcessCanceledException processcanceledexception)
                {
                    return;
                }

                ApplicationManager.getApplication().invokeLater(new Runnable()
                {
                    public void run()
                    {
                        CommandProcessor.getInstance().executeCommand(myProject, new Runnable()
                        {
                            public void run()
                            {
                                CommandProcessor.getInstance().markCurrentCommandAsComplex(myProject);
                                try
                                {
                                    ApplicationManager.getApplication().runWriteAction(writeAction);
                                }
                                catch (ProcessCanceledException processcanceledexception)
                                {
                                    return;
                                }
                                if (postProcess != null)
                                {
                                    ApplicationManager.getApplication().invokeLater(postProcess);
                                }
                            }
                        }, title, null);
                    }
                }, modalityState);
            }
        };

        (new Thread(runnable, title)).start();
    }

    private static final Logger logger = Logger.getInstance(AbstractFileProcessor.class.getName());
}