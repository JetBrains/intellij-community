/*
 * Copyright 2000-2014 JetBrains s.r.o.
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

package com.maddyhome.idea.copyright.util;

import com.intellij.lang.Commenter;
import com.intellij.lang.LanguageCommenters;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.*;
import com.intellij.openapi.project.ProjectCoreUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiCodeFragment;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.PsiFile;
import com.intellij.util.messages.MessageBus;
import com.maddyhome.idea.copyright.CopyrightUpdaters;
import com.maddyhome.idea.copyright.options.LanguageOptions;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class FileTypeUtil {
  public static synchronized FileTypeUtil getInstance() {
    return ServiceManager.getService(FileTypeUtil.class);
  }

  public FileTypeUtil(MessageBus bus) {
    createMappings();
    bus.connect().subscribe(FileTypeManager.TOPIC, new FileTypeListener.Adapter() {
      @Override
      public void fileTypesChanged(@NotNull FileTypeEvent event) {
        types = null;
      }
    });
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

    if (template.length() > 0) {
      String[] lines = template.split("\n", -1);
      for (String line : lines) {
        if (options.isTrim()) {
          line = line.trim();
        }
        line = StringUtil.trimStart(StringUtil.trimStart(line, pre.toString()), open);
        line = StringUtil.trimEnd(line, close);
        preview.append(leader).append(pre);
        int len = 0;
        if (pre.length() > 0 && line.length() > 0) {
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

  public boolean isSupportedFile(VirtualFile file) {
    if (file == null || file.isDirectory()) {
      return false;
    }

    if (ProjectCoreUtil.isProjectOrWorkspaceFile(file)) return false;
    FileType type = file.getFileType();

    return getMap().get(type.getName()) != null;
  }

  public static boolean isSupportedFile(PsiFile file) {
    if (file == null || file instanceof PsiDirectory || file instanceof PsiCodeFragment) {
      return false;
    }
    final VirtualFile virtualFile = file.getVirtualFile();
    if (virtualFile == null) return false;
    if (ProjectCoreUtil.isProjectOrWorkspaceFile(virtualFile)) return false;
    return isSupportedType(virtualFile.getFileType());
  }

  public FileType[] getSupportedTypes() {
    HashSet<FileType> set = new HashSet<FileType>(getMap().values());
    return set.toArray(new FileType[set.size()]);
  }

  public FileType getFileTypeByFile(VirtualFile file) {
    FileType type = file.getFileType();

    return getFileTypeByType(type);
  }

  public FileType getFileTypeByType(FileType type) {
    return getMap().get(type.getName());
  }

  public String getFileTypeNameByName(String name) {
    FileType type = getMap().get(name);

    return type != null ? type.getName() : name;
  }

  public static boolean hasBlockComment(FileType fileType) {
    Commenter commenter = getCommenter(fileType);

    return commenter != null && commenter.getBlockCommentPrefix() != null;
  }

  public static boolean hasLineComment(FileType fileType) {
    Commenter commenter = getCommenter(fileType);

    return commenter != null && commenter.getLineCommentPrefix() != null;
  }

  public boolean allowSeparators(FileType fileType) {
    FileType type = getFileTypeByType(fileType);

    return !noSeparators.contains(type);
  }

  private static Commenter getCommenter(FileType fileType) {
    if (fileType instanceof LanguageFileType) {
      return LanguageCommenters.INSTANCE.forLanguage(((LanguageFileType)fileType).getLanguage());
    }


    return null;
  }

  private void createMappings() {
    Set<FileType> maps = new HashSet<FileType>();
    maps.add(StdFileTypes.DTD);
    maps.add(StdFileTypes.XML);

    mappings.put(StdFileTypes.XML, maps);

    maps = new HashSet<FileType>();
    maps.add(StdFileTypes.HTML);
    maps.add(StdFileTypes.XHTML);

    mappings.put(StdFileTypes.HTML, maps);

    maps = new HashSet<FileType>();
    maps.add(StdFileTypes.JSP);

    mappings.put(StdFileTypes.JSP, maps);

    noSeparators.add(StdFileTypes.XML);
    noSeparators.add(StdFileTypes.HTML);
    noSeparators.add(StdFileTypes.JSP);
    noSeparators.add(StdFileTypes.JSPX);
  }

  private static boolean isSupportedType(FileType type) {
    if (type.isBinary() || type.getName().indexOf("IDEA") >= 0 || "GUI_DESIGNER_FORM".equals(type.getName())) {
      return false;
    }
    else {
      Commenter commenter = getCommenter(type);
      boolean hasComment = commenter != null &&
                           (commenter.getLineCommentPrefix() != null || commenter.getBlockCommentPrefix() != null);
      if (!hasComment) {
        return false;
      }
      if (type.equals(StdFileTypes.DTD)) {
        return true;
      }
      if (type.equals(StdFileTypes.HTML)) {
        return true;
      }
      if (type.equals(StdFileTypes.XHTML)) {
        return true;
      }
      if (type.equals(StdFileTypes.PROPERTIES)) {
        return true;
      }
      if ("JavaScript".equals(type.getName())) {
        return true;
      }
      return CopyrightUpdaters.INSTANCE.forFileType(type) != null;
    }
  }

  private void loadFileTypes() {
    logger.debug("loadFileTypes");
    Map<String, FileType> map = new HashMap<String, FileType>();
    for (FileType ftype : FileTypeManager.getInstance().getRegisteredFileTypes()) {
      // Ignore binary files
      // Ignore IDEA specific file types (PROJECT, MODULE, WORKSPACE)
      // Ignore GUI Designer files
      if (isSupportedType(ftype)) {
        logger.debug("adding " + ftype.getName());
        Iterator<FileType> iter = mappings.keySet().iterator();
        FileType type = ftype;
        while (iter.hasNext()) {
          FileType fileType = iter.next();
          Set<FileType> maps = mappings.get(fileType);
          if (maps.contains(ftype)) {
            type = fileType;
            break;
          }
        }
        map.put(ftype.getName(), type);
      }
      else {
        logger.debug("ignoring " + ftype.getName());
      }
    }
    types = map;
  }

  public FileType getFileTypeByName(String name) {
    return getMap().get(name);
  }

  @NotNull
  private Map<String, FileType> getMap() {
    if (types == null) loadFileTypes();
    return types;
  }

  public static class SortByName implements Comparator<FileType> {
    @Override
    public int compare(FileType a, FileType b) {
      return a.getName().compareToIgnoreCase(b.getName());
    }
  }

  private Map<String, FileType> types;
  private final Map<FileType, Set<FileType>> mappings = new HashMap<FileType, Set<FileType>>();
  private final Set<FileType> noSeparators = new HashSet<FileType>();

  private static final Logger logger = Logger.getInstance(FileTypeUtil.class.getName());
}