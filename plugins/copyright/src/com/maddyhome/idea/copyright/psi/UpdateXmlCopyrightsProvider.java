// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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

public final class UpdateXmlCopyrightsProvider extends UpdateCopyrightsProvider {
  @Override
  public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
    return new UpdateXmlFileCopyright(project, module, file, options);
  }

  @Override
  public LanguageOptions getDefaultOptions() {
    return createDefaultOptions(false);
  }

  public static class UpdateXmlFileCopyright extends UpdatePsiFileCopyright {
    public UpdateXmlFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options) {
      super(project, module, root, options);
    }

    @Override
    protected boolean accept() {
      return getFile() instanceof XmlFile;
    }

    @Override
    protected void scanFile() {
      logger.debug("updating " + getFile().getVirtualFile());


      XmlDoctype doctype = null;
      PsiElement root = null;

      XmlDocument doc = ((XmlFile)getFile()).getDocument();
      PsiElement elem = doc.getFirstChild();
      while (elem != null) {
        if (elem instanceof XmlProlog) {
          PsiElement prolog = elem.getFirstChild();
          while (prolog != null) {
            if (prolog instanceof XmlDoctype) {
              doctype = (XmlDoctype)prolog;
            }

            prolog = prolog.getNextSibling();
          }
        }
        else if (elem instanceof XmlTag || elem instanceof XmlElementDecl || elem instanceof XmlAttributeDecl) {
          root = elem;
          break;
        }

        elem = elem.getNextSibling();
      }

      PsiElement first = doc.getFirstChild();
      if (root == null) {
        root = doc.getLastChild();
      }

      int location = getLanguageOptions().getFileLocation();
      if (doctype != null) {
        checkComments(first, doctype, location == XmlOptions.LOCATION_BEFORE_DOCTYPE);
        first = doctype;
      }
      else if (location == XmlOptions.LOCATION_BEFORE_DOCTYPE) {
        location = XmlOptions.LOCATION_BEFORE_ROOTTAG;
      }

      if (root != null) {
        checkComments(first, root, location == XmlOptions.LOCATION_BEFORE_ROOTTAG);
      }
      else if (location == XmlOptions.LOCATION_BEFORE_ROOTTAG) {
        // If we get here we have an empty file
        checkComments(first, first, true);
      }
    }

    @Override
    protected PsiElement getPreviousSibling(PsiElement element) {
      if (element == null) return null;

      PsiElement res = element.getPrevSibling();
      if (res != null) {
        if (res instanceof XmlProlog) {
          XmlProlog prolog = (XmlProlog)res;
          if (prolog.getChildren().length > 0) {
            res = prolog.getLastChild();
          }
          else {
            res = prolog.getPrevSibling();
          }
        }
      }
      else {
        if (element.getParent() instanceof XmlProlog) {
          res = element.getParent().getPrevSibling();
        }
      }

      return res;
    }

    @Override
    protected PsiElement getNextSibling(PsiElement element) {
      if (element == null) return null;

      PsiElement res = element instanceof XmlProlog ? element : element.getNextSibling();
      if (res != null) {
        if (res instanceof XmlProlog) {
          XmlProlog prolog = (XmlProlog)res;
          if (prolog.getChildren().length > 0) {
            res = prolog.getFirstChild();
          }
          else {
            res = prolog.getNextSibling();
          }
        }
      }
      else {
        if (element.getParent() instanceof XmlProlog) {
          res = element.getParent().getNextSibling();
        }
      }

      return res;
    }

    private static final Logger logger = Logger.getInstance(UpdateXmlFileCopyright.class.getName());
  }
}
