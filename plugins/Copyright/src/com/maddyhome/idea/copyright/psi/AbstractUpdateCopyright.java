package com.maddyhome.idea.copyright.psi;

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

import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.util.FileTypeUtil;
import com.maddyhome.idea.copyright.util.VelocityHelper;

public abstract class AbstractUpdateCopyright implements UpdateCopyright
{
    public VirtualFile getRoot()
    {
        return root;
    }

    public PsiManager getManager()
    {
        return manager;
    }

    protected AbstractUpdateCopyright(Project project, Module module, VirtualFile root, Options options)
    {
        this.project = project;
        this.module = module;
        this.root = root;
        this.options = options;
        manager = PsiManager.getInstance(project);
    }


    protected String getCommentText(String prefix, String suffix)
    {
        if (commentText == null)
        {
            FileType ftype = FileTypeUtil.getInstance().getFileTypeByFile(root);
            LanguageOptions opts = options.getMergedOptions(ftype.getName());
            String base = opts.getNotice();
            if (base.length() > 0)
            {
                String expanded = VelocityHelper.evaluate(manager.findFile(root), project, module, opts.getNotice());
                String cmt = opts.getFileTypeOverride() == LanguageOptions.USE_CUSTOM ? expanded :
                    FileTypeUtil.buildComment(root.getFileType(), opts.isUseAlternate(), expanded,
                    opts.getTemplateOptions());
                commentText = prefix + cmt + suffix;
            }
            else
            {
                commentText = "";
            }
        }

        return commentText;
    }

    protected void resetCommentText()
    {
        commentText = null;
    }

    protected static int countNewline(String text)
    {
        int cnt = 0;
        for (int i = 0; i < text.length(); i++)
        {
            if (text.charAt(i) == '\n')
            {
                cnt++;
            }
        }

        return cnt;
    }

    private String commentText = null;
    private Project project;
    private Module module;
    private VirtualFile root;
    private Options options;
    private PsiManager manager;
}