// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.util;

import com.intellij.ide.highlighter.HtmlFileType;
import com.intellij.ide.highlighter.XmlFileType;
import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.Disposable;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.LanguageFileType;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.project.ProjectUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

public class FileTypeUtil implements Disposable {

  public static synchronized FileTypeUtil getInstance() {
    return ServiceManager.getService(FileTypeUtil.class);
  }

  public FileTypeUtil() {
    createMappings();
  }

  public static String buildComment(FileType type, String template, LanguageOptions options) {
    Commenter commenter = getCommenter(type);
    if (commenter == null) {
      return "<No comments>";
    }

    String bs = commenter.getBlockCommentPrefix();
    String be = commenter.getBlockCommentSuffix();
    String ls = commenter.getLineCommentPrefix();

    if ((bs == null || be == null) && ls == null) {
      return "<No comments>";
    }

    boolean allowBlock = bs != null && be != null;
    boolean allowLine = ls != null;
    if (allowLine && !allowBlock) {
      bs = ls;
      be = ls;
    }

    boolean allowSeparator = getInstance().allowSeparators(type);
    String filler = options.getFiller();
    if (!allowSeparator) {
      if (options.getFiller() == LanguageOptions.DEFAULT_FILLER) {
        filler = "~";
      }
    }

    boolean isBlock = options.isBlock();
    boolean isPrefix = options.isPrefixLines();
    if (isBlock && !allowBlock) {
      isPrefix = true;
    }
    boolean isBox = options.isBox() && options.isSeparateBefore() && options.isSeparateAfter() &&
                    options.getLenBefore() == options.getLenAfter();

    StringBuilder preview = new StringBuilder(80);
    String open = isBlock ? bs : allowLine ? ls : bs;
    String close = isBlock ? be : allowLine ? ls : be;
    StringBuilder pre = new StringBuilder(5);
    StringBuilder leader = new StringBuilder(5);
    StringBuilder post = new StringBuilder(5);
    if (filler == LanguageOptions.DEFAULT_FILLER) {
      filler = open.substring(open.length() - 1);
    }
    int offset = 0;
    if (isBlock) {
      int pos = open.length() - 1;
      pre.append(allowBlock ? filler : open.charAt(pos));
      while (pos > 0 && open.charAt(pos) == open.charAt(open.length() - 1)) {
        pos--;
        offset++;
      }
      while (open.length() > 1 && pos >= 0) {
        leader.append(' ');
        pos--;
      }
      post.append(filler);
      if (!isPrefix) {
        pre = new StringBuilder(0);
      }
      if (!allowBlock) {
        close = filler;
      }
    }
    else {
      if (allowLine) {
        close = filler;
      }
      pre.append(open);
      post.append(close);
    }

    int diff = 0;
    if (options.isSeparateBefore()) {
      if (isBlock && isBox && allowBlock) {
        diff = close.length() - offset;
      }

      preview.append(open);
      for (int i = open.length() + 1; i <= options.getLenBefore() - diff - post.length(); i++) {
        preview.append(filler);
      }

      preview.append(post);

      preview.append('\n');
    }
    else if (isBlock) {
      preview.append(open).append('\n');
    }

    if (!template.isEmpty()) {
      String[] lines = template.split("\n", -1);
      for (String line : lines) {
        if (options.isTrim()) {
          line = line.trim();
        }
        line = StringUtil.trimStart(StringUtil.trimStart(line, pre.toString()), open);
        line = StringUtil.trimEnd(line, close);
        preview.append(leader).append(pre);
        int len = 0;
        if (pre.length() > 0 && !line.isEmpty()) {
          preview.append(' ');
          len++;
        }
        preview.append(line);
        len += line.length() + leader.length() + pre.length();
        if (isBox && len < options.getLenBefore() - diff) {
          for (; len < options.getLenBefore() - diff - post.length(); len++) {
            preview.append(' ');
          }
          if (isBlock || allowLine) {
            preview.append(post.substring(0, options.getLenBefore() - diff - len));
          }
        }

        if (!isBlock && !allowLine) {
          if (preview.charAt(preview.length() - 1) != ' ') {
            preview.append(' ');
          }
          preview.append(close);
        }

        preview.append('\n');
      }
    }

    preview.append(leader);
    if (options.isSeparateAfter()) {
      preview.append(pre);
      for (int i = leader.length() + pre.length(); i < options.getLenAfter() - close.length(); i++) {
        preview.append(filler);
      }
      preview.append(close);
      preview.append('\n');
    }
    else if (isBlock) {
      if (!allowBlock) {
        preview.append(pre).append('\n');
      }
      else {
        preview.append(close).append('\n');
      }
    }

    return preview.substring(0, preview.length() - 1);
  }

  public static boolean isSupportedFile(@NotNull VirtualFile file) {
    if (file.isDirectory()) {
      return false;
    }

    if (ProjectUtil.isProjectOrWorkspaceFile(file)) return false;
    return isSupportedType(file.getFileType());
  }

  public static boolean isSupportedFile(@Nullable PsiFile file) {
    if (file == null || file instanceof PsiDirectory || file instanceof PsiCodeFragment) {
      return false;
    }
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (ProjectUtil.isProjectOrWorkspaceFile(virtualFile)) return false;
    return isSupportedType(virtualFile.getFileType());
  }

  public static Set<FileType> getSupportedTypes() {
    return CopyrightUpdaters.INSTANCE.getAllRegisteredExtensions().keySet();
  }

  public static boolean hasBlockComment(FileType fileType) {
    Commenter commenter = getCommenter(fileType);

    return commenter != null && commenter.getBlockCommentPrefix() != null;
  }

  public boolean allowSeparators(FileType fileType) {
    return !noSeparators.contains(fileType);
  }

  private static Commenter getCommenter(FileType fileType) {
    if (fileType instanceof LanguageFileType) {
      return LanguageCommenters.INSTANCE.forLanguage(((LanguageFileType)fileType).getLanguage());
    }


    return null;
  }

  private void createMappings() {
    noSeparators.add(XmlFileType.INSTANCE);
    noSeparators.add(HtmlFileType.INSTANCE);
    noSeparators.add(StdFileTypes.JSP);
    noSeparators.add(StdFileTypes.JSPX);
  }

  private static boolean isSupportedType(FileType type) {
    if (type.isBinary() || type.getName().contains("IDEA") || "GUI_DESIGNER_FORM".equals(type.getName())) {
      return false;
    }
    else {
      if (CopyrightUpdaters.INSTANCE.forFileType(type) == null) {
        return false;
      }
      Commenter commenter = getCommenter(type);
      return commenter != null &&
             (commenter.getLineCommentPrefix() != null || commenter.getBlockCommentPrefix() != null);
    }
  }

  public static class SortByName implements Comparator<FileType> {
    @Override
    public int compare(FileType a, FileType b) {
      return a.getName().compareToIgnoreCase(b.getName());
    }
  }

  private final Set<FileType> noSeparators = new HashSet<>();
  @Override
  public void dispose() { }
}
