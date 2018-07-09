/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
import com.intellij.psi.xml.*;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.XmlOptions;

public class UpdateXmlCopyrightsProvider extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new UpdateXmlFileCopyright(project, module, file, options);
  }

  @Override
  public LanguageOptions getDefaultOptions() {
    return createDefaultOptions(false);
  }

  public static class UpdateXmlFileCopyright extends UpdatePsiFileCopyright
  {
      public UpdateXmlFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options)
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
                  break;
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

      private static final Logger logger = Logger.getInstance(UpdateXmlFileCopyright.class.getName());
  }
}
