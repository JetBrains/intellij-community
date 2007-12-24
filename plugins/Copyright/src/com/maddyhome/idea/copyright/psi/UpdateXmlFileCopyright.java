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
import com.intellij.psi.xml.XmlAttributeDecl;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlElementDecl;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.intellij.psi.xml.XmlTag;
import com.maddyhome.idea.copyright.options.Options;
import com.maddyhome.idea.copyright.options.XmlOptions;

public class UpdateXmlFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateXmlFileCopyright(Project project, Module module, VirtualFile root, Options options)
    {
        super(project, module, root, options);
    }

    protected boolean accept()
    {
        return getFile() instanceof XmlFile;
    }

    protected void scanFile()
    {
        logger.debug("updating " + getFile().getVirtualFile());


        XmlDoctype doctype = null;
        PsiElement root = null;

        XmlDocument doc = ((XmlFile)getFile()).getDocument();
        PsiElement elem = doc.getFirstChild();
        while (elem != null)
        {
            if (elem instanceof XmlProlog)
            {
                PsiElement prolog = elem.getFirstChild();
                while (prolog != null)
                {
                    if (prolog instanceof XmlDoctype)
                    {
                        doctype = (XmlDoctype)prolog;
                    }

                    prolog = prolog.getNextSibling();
                }
            }
            else if (elem instanceof XmlTag || elem instanceof XmlElementDecl || elem instanceof XmlAttributeDecl)
            {
                root = elem;
            }

            elem = elem.getNextSibling();
        }

        PsiElement first = doc.getFirstChild();
        if (root == null)
        {
            root = doc.getLastChild();
        }

        int location = getLanguageOptions().getFileLocation();
        if (doctype != null)
        {
            checkComments(first, doctype, location == XmlOptions.LOCATION_BEFORE_DOCTYPE);
            first = doctype;
        }
        else if (location == XmlOptions.LOCATION_BEFORE_DOCTYPE)
        {
            location = XmlOptions.LOCATION_BEFORE_ROOTTAG;
        }

        if (root != null)
        {
            checkComments(first, root, location == XmlOptions.LOCATION_BEFORE_ROOTTAG);
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
        if (res != null)
        {
            if (res instanceof XmlProlog)
            {
                XmlProlog prolog = (XmlProlog)res;
                if (prolog.getChildren().length > 0)
                {
                    res = prolog.getLastChild();
                }
                else
                {
                    res = prolog.getPrevSibling();
                }
            }
        }
        else
        {
            if (element.getParent() instanceof XmlProlog)
            {
                res = element.getParent().getPrevSibling();
            }
        }

        return res;
    }

    protected PsiElement getNextSibling(PsiElement element)
    {
        if (element == null) return null;

        PsiElement res = element instanceof XmlProlog ? element : element.getNextSibling();
        if (res != null)
        {
            if (res instanceof XmlProlog)
            {
                XmlProlog prolog = (XmlProlog)res;
                if (prolog.getChildren().length > 0)
                {
                    res = prolog.getFirstChild();
                }
                else
                {
                    res = prolog.getNextSibling();
                }
            }
        }
        else
        {
            if (element.getParent() instanceof XmlProlog)
            {
                res = element.getParent().getNextSibling();
            }
        }

        return res;
    }

    private static Logger logger = Logger.getInstance(UpdateXmlFileCopyright.class.getName());
}