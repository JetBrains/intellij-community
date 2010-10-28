/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiWhiteSpace;
import com.intellij.psi.impl.source.jsp.jspXml.JspDirective;
import com.intellij.psi.jsp.JspFile;
import com.intellij.psi.xml.*;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.XmlOptions;

public class UpdateJspFileCopyright extends UpdatePsiFileCopyright
{
    public UpdateJspFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options)
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

        XmlDocument doc = ((XmlFile)getFile()).getDocument();
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
            else if (elem instanceof XmlTag && !(elem instanceof JspDirective))
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

    private static final Logger logger = Logger.getInstance(UpdateJspFileCopyright.class.getName());
  public static class UpdateJspCopyrightsProvider extends UpdateCopyrightsProvider {

    @Override
    public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
      return new UpdateJspFileCopyright(project, module, file, options);
    }

    @Override
    public LanguageOptions getDefaultOptions() {
      return createDefaultOptions(false);
    }
  }
}