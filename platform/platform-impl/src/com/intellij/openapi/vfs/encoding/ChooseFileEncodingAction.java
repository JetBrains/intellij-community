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

/*
 * Created by IntelliJ IDEA.
 * User: cdr
 * Date: Jul 19, 2007
 * Time: 5:53:46 PM
 */
package com.intellij.openapi.vfs.encoding;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class ChooseFileEncodingAction extends ComboBoxAction {
  private final VirtualFile myVirtualFile;

  public ChooseFileEncodingAction(VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  @Override
  public abstract void update(final AnActionEvent e);

  @NotNull
  private static Pair<Charset, String> checkFileType(@NotNull VirtualFile virtualFile) {
    FileType fileType = virtualFile.getFileType();
    if (fileType.isBinary()) return Pair.create(null, "binary file");
    if (fileType == StdFileTypes.GUI_DESIGNER_FORM) return Pair.create(CharsetToolkit.UTF8_CHARSET, "IDEA GUI Designer form");
    if (fileType == StdFileTypes.IDEA_MODULE) return Pair.create(CharsetToolkit.UTF8_CHARSET, "IDEA module file");
    if (fileType == StdFileTypes.IDEA_PROJECT) return Pair.create(CharsetToolkit.UTF8_CHARSET, "IDEA project file");
    if (fileType == StdFileTypes.IDEA_WORKSPACE) return Pair.create(CharsetToolkit.UTF8_CHARSET, "IDEA workspace file");

    if (fileType == StdFileTypes.PROPERTIES) return Pair.create(virtualFile.getCharset(), ".properties file");

    if (fileType == StdFileTypes.XML
        || fileType == StdFileTypes.JSPX && fileType != FileTypes.PLAIN_TEXT // in community tests JSPX==PLAIN_TEXT
      ) {
      return Pair.create(virtualFile.getCharset(), "XML file");
    }
    return Pair.create(null, null);
  }

  private void fillCharsetActions(@NotNull DefaultActionGroup group,
                                  @Nullable VirtualFile virtualFile,
                                  @NotNull List<Charset> charsets,
                                  @Nullable final Condition<Charset> charsetFilter,
                                  @NotNull String pattern) {
    for (final Charset slave : charsets) {
      ChangeFileEncodingTo action = new ChangeFileEncodingTo(virtualFile, slave, pattern) {
        {
          if (charsetFilter != null && !charsetFilter.value(slave)) {
            getTemplatePresentation().setIcon(AllIcons.General.Warning);
          }
        }

        @Override
        public void update(AnActionEvent e) {
        }

        @Override
        protected void chosen(final VirtualFile file, @NotNull final Charset charset) {
          ChooseFileEncodingAction.this.chosen(file, charset);
        }
      };
      group.add(action);
    }
  }

  @Nullable("null means enabled, notnull means disabled and contains error message")
  public static String checkCanConvert(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return "file is a directory";
    }
    String reason = LoadTextUtil.wasCharsetDetectedFromBytes(virtualFile);
    if (reason == null) {
      return null;
    }
    String failReason = null;

    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    if (charsetFromContent != null) {
      failReason = "hard coded in text, encoding: {0}";
    }
    else {
      Pair<Charset, String> check = checkFileType(virtualFile);
      if (check.second != null) {
        failReason = check.second;
      }
    }

    if (failReason != null) {
      return MessageFormat.format(failReason, charsetFromContent == null ? "" : charsetFromContent.displayName());
    }
    return null;
  }

  @NotNull
  // returns existing charset (null means N/A), failReason: null means enabled, notnull means disabled and contains error message
  public static Pair<Charset, String> checkCanReload(@NotNull VirtualFile virtualFile) {
    if (virtualFile.isDirectory()) {
      return Pair.create(null, "file is a directory");
    }
    FileDocumentManager documentManager = FileDocumentManager.getInstance();
    Document document = documentManager.getDocument(virtualFile);
    if (document == null) return Pair.create(null, "binary file");
    Charset charsetFromContent = ((EncodingManagerImpl)EncodingManager.getInstance()).computeCharsetFromContent(virtualFile);
    Charset existing = charsetFromContent;
    String failReason = LoadTextUtil.wasCharsetDetectedFromBytes(virtualFile);
    if (failReason != null) {
      // no point changing encoding if it was auto-detected
      existing = virtualFile.getCharset();
    }
    else if (charsetFromContent != null) {
      failReason = "hard coded in text";
    }
    else {
      Pair<Charset, String> fileTypeCheck = checkFileType(virtualFile);
      if (fileTypeCheck.second != null) {
        failReason = fileTypeCheck.second;
        existing = fileTypeCheck.first;
      }
    }
    if (failReason != null) {
      return Pair.create(existing, failReason);
    }
    return Pair.create(virtualFile.getCharset(), null);
  }

  private class ClearThisFileEncodingAction extends AnAction {
    private final VirtualFile myFile;

    private ClearThisFileEncodingAction(@Nullable VirtualFile file, @NotNull String clearItemText) {
      super(clearItemText, "Clear " + (file == null ? "default" : "file '"+file.getName()+"'") + " encoding.", null);
      myFile = file;
    }

    @Override
    public void actionPerformed(final AnActionEvent e) {
      chosen(myFile, NO_ENCODING);
    }
  }

  public static final Charset NO_ENCODING = new Charset("NO_ENCODING", null) {
    @Override
    public boolean contains(final Charset cs) {
      return false;
    }

    @Override
    public CharsetDecoder newDecoder() {
      return null;
    }

    @Override
    public CharsetEncoder newEncoder() {
      return null;
    }
  };
  protected abstract void chosen(@Nullable VirtualFile virtualFile, @NotNull Charset charset);

  @NotNull
  public DefaultActionGroup createGroup(@Nullable("null means do not show 'clear' text") String clearItemText,
                                        @Nullable Condition<Charset> charsetFilter,
                                        @NotNull String pattern,
                                        Charset alreadySelected) {
    DefaultActionGroup group = new DefaultActionGroup();
    List<Charset> favorites = new ArrayList<Charset>(EncodingManager.getInstance().getFavorites());
    Collections.sort(favorites);
    Charset current = myVirtualFile == null ? null : myVirtualFile.getCharset();
    favorites.remove(current);
    favorites.remove(alreadySelected);

    if (clearItemText != null) {
      group.add(new ClearThisFileEncodingAction(myVirtualFile, clearItemText));
    }
    if (favorites.isEmpty() && clearItemText == null) {
      fillCharsetActions(group, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()), charsetFilter, pattern);
    }
    else {
      fillCharsetActions(group, myVirtualFile, favorites, charsetFilter, pattern);

      DefaultActionGroup more = new DefaultActionGroup("more", true);
      group.add(more);
      fillCharsetActions(more, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()), charsetFilter, pattern);
    }
    return group;
  }
}
