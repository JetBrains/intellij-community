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

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlText;
import com.intellij.psi.xml.XmlToken;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.options.XmlOptions;

public class UpdateJspFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateJspFileCopyright(Project project, Module module, VirtualFile root, Options options)
    {
        super(project, module, root, options);
    }

    protected boolean accept()
    {
        return getFile() instanceof JspFile;
    }

    protected void scanFile()
    {
        logger.debug("updating " + getFile().getVirtualFile());

        XmlDocument doc = ((JspFile)getFile()).getDocument();
        XmlTag root = doc.getRootTag();
        if (root == null)
        {
            return;
        }

        PsiElement elem = root.getFirstChild();
        PsiElement docTypeStart = null;
        PsiElement docTypeEnd = null;
        PsiElement firstTag = null;
        while (elem != null)
        {
            if (elem instanceof XmlToken)
            {
                if ("<!DOCTYPE".equals(elem.getText()))
                {
                    docTypeStart = elem;
                    while ((elem = getNextSibling(elem)) != null)
                    {
                        if (elem instanceof PsiWhiteSpace) continue;
                        if (elem instanceof XmlToken)
                        {
                            if (elem.getText().endsWith(">"))
                            {
                                elem = getNextSibling(elem);
                                docTypeEnd = elem;
                                break;
                            }
                            else if (elem.getText().startsWith("<"))
                            {
                                docTypeEnd = elem;
                                break;
                            }
                        }
                        else
                        {
                            break;
                        }
                    }
                    continue;
                }
                else
                {
                    firstTag = elem;
                    break;
                }
            }
            else if (elem instanceof XmlTag)
            {
                firstTag = elem;
                break;
            }
            elem = getNextSibling(elem);
        }

        PsiElement first = root.getFirstChild();

        int location = getLanguageOptions().getFileLocation();
        if (docTypeStart != null)
        {
            checkComments(first, docTypeStart, location == XmlOptions.LOCATION_BEFORE_DOCTYPE);
            first = docTypeEnd;
        }
        else if (location == XmlOptions.LOCATION_BEFORE_DOCTYPE)
        {
            location = XmlOptions.LOCATION_BEFORE_ROOTTAG;
        }

        if (firstTag != null)
        {
            checkComments(first, firstTag, location == XmlOptions.LOCATION_BEFORE_ROOTTAG);
        }
        else if (location == XmlOptions.LOCATION_BEFORE_ROOTTAG)
        {
            // If we get here we have an empty file
            checkComments(first, first, true);
        }
    }

    protected PsiElement getPreviousSibling(PsiElement element)
    {
        if (element == null) return null;

        PsiElement res = element.getPrevSibling();
        if (res == null)
        {
            if (element.getParent() instanceof XmlText)
            {
                res = element.getParent().getPrevSibling();
            }
        }

        if (res instanceof XmlText)
        {
            XmlText text = (XmlText)res;
            if (text.getLastChild() != null)
            {
                res = text.getLastChild();
            }
            else
            {
                res = text.getPrevSibling();
            }
        }

        return res;
    }

    protected PsiElement getNextSibling(PsiElement element)
    {
        if (element == null) return null;

        PsiElement res = element instanceof XmlText ? element.getFirstChild() : element.getNextSibling();
        if (res instanceof XmlText)
        {
            if (res.getFirstChild() != null)
            {
                res = res.getFirstChild();
            }
            else
            {
                res = res.getNextSibling();
            }
        }

        if (res == null)
        {
            if (element.getParent() instanceof XmlText)
            {
                res = element.getParent().getNextSibling();
            }
        }

        return res;
    }

    private static Logger logger = Logger.getInstance(UpdateJspFileCopyright.class.getName());
}