// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.maddyhome.idea.copyright.psi;

import com.intellij.copyright.CopyrightManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.maddyhome.idea.copyright.CopyrightProfile;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import com.maddyhome.idea.copyright.pattern.EntityUtil;
import com.maddyhome.idea.copyright.pattern.VelocityHelper;
import com.maddyhome.idea.copyright.util.FileTypeUtil;

public abstract class AbstractUpdateCopyright implements UpdateCopyright {
  private String commentText = null;
  private final Project project;
  private final Module module;
  private final VirtualFile root;
  private final CopyrightProfile myCopyrightProfile;
  private final PsiManager manager;

  protected AbstractUpdateCopyright(Project project, Module module, VirtualFile root, CopyrightProfile copyrightProfile) {
    this.project = project;
    this.module = module;
    this.root = root;
    myCopyrightProfile = copyrightProfile;
    manager = PsiManager.getInstance(project);
  }

  protected String getCommentText(String prefix, String suffix) {
    return getCommentText(prefix, suffix, null);
  }

  protected String getCommentText(String prefix, String suffix, String oldComment) {
    if (commentText == null) {
      FileType ftype = root.getFileType();
      LanguageOptions opts = CopyrightManager.getInstance(project).getOptions().getMergedOptions(ftype.getName());
      String notice = myCopyrightProfile.getNotice();
      String base = notice != null ? EntityUtil.decode(notice) : "";
      if (base.isEmpty()) {
        commentText = "";
      }
      else {
        String expanded = VelocityHelper.evaluate(manager.findFile(root), project, module, base, oldComment);
        String cmt = FileTypeUtil.buildComment(root.getFileType(), expanded, opts);
        commentText = StringUtil.convertLineSeparators(prefix + cmt + suffix);
      }
    }

    return commentText;
  }


  @Override
  public VirtualFile getRoot() {
    return root;
  }

  public PsiManager getManager() {
    return manager;
  }

  protected void resetCommentText() {
    commentText = null;
  }

  protected static int countNewline(String text) {
    int cnt = 0;
    for (int i = 0; i < text.length(); i++) {
      if (text.charAt(i) == '\n') {
        cnt++;
      }
    }

    return cnt;
  }
}
