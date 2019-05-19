// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.psi;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiComment;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlDoctype;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlProlog;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.options.XmlOptions;

import java.util.ArrayList;

public class UpdateJspxFileCopyright extends UpdateJspFileCopyright {
  public UpdateJspxFileCopyright(Project project, Module module, VirtualFile root, CopyrightProfile options) {
    super(project, module, root, options);
  }

  @Override
  protected void scanFile() {
    logger.debug("updating " + getFile().getVirtualFile());

    XmlDocument doc = ((XmlFile)getFile()).getDocument();
    XmlProlog xmlProlog = doc.getProlog();
    if (xmlProlog == null) {
      return;
    }

    PsiElement elem = xmlProlog.getFirstChild();
    PsiElement docTypeStart = null;
    while (elem != null) {
      if (elem instanceof XmlDoctype) {
        docTypeStart = elem;
        break;
      }
      elem = getNextSibling(elem);
    }

    PsiElement first = xmlProlog.getFirstChild();

    int location = getLanguageOptions().getFileLocation();

    if (docTypeStart != null) {
      final ArrayList<PsiComment> comments = new ArrayList<>();
      collectComments(doc.getFirstChild(), xmlProlog, comments);
      collectComments(first, docTypeStart, comments);
      checkComments(first, location == XmlOptions.LOCATION_BEFORE_DOCTYPE, comments);
      checkComments(docTypeStart, doc.getRootTag(), location == XmlOptions.LOCATION_BEFORE_ROOTTAG);
      return;
    }
    else if (location == XmlOptions.LOCATION_BEFORE_DOCTYPE) {
      location = XmlOptions.LOCATION_BEFORE_ROOTTAG;
    }
    final ArrayList<PsiComment> comments = new ArrayList<>();
    collectComments(doc.getFirstChild(), xmlProlog, comments);
    collectComments(first, doc.getRootTag(), comments);
    checkComments(doc.getRootTag(), location == XmlOptions.LOCATION_BEFORE_ROOTTAG, comments);
  }

  private static final Logger logger = Logger.getInstance(UpdateJspxFileCopyright.class.getName());

  public static class UpdateJspxCopyrightsProvider extends UpdateCopyrightsProvider {

    @Override
    public UpdateCopyright createInstance(Project project, Module module, VirtualFile file, FileType base, CopyrightProfile options) {
      return new UpdateJspxFileCopyright(project, module, file, options);
    }

    @Override
    public LanguageOptions getDefaultOptions() {
      return createDefaultOptions(false);
    }
  }
}
