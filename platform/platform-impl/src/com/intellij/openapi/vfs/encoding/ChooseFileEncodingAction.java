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

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.editor.Document;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.fileTypes.FileType;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.fileTypes.StdFileTypes;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
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

  public void update(final AnActionEvent e) {
    Pair<String, Boolean> result = update(myVirtualFile);

    boolean enabled = result.second;
    if (myVirtualFile != null) {
      Charset charset = cachedCharsetFromContent(myVirtualFile);
      String prefix = charset == null ? "" : "Encoding (auto-detected):";
      if (charset == null) charset = myVirtualFile.getCharset();
      e.getPresentation().setText(prefix + " " + charset.toString());
    }
    e.getPresentation().setEnabled(enabled);
    e.getPresentation().setDescription(result.first);
  }

  public static boolean isEnabled(@Nullable VirtualFile virtualFile) {
    if (virtualFile == null) {
      return false;
    }
    boolean enabled = true;
    Charset charset = cachedCharsetFromContent(virtualFile);
    if (charset != null) {
      enabled = false;
    }
    else if (!virtualFile.isDirectory()) {
      FileType fileType = FileTypeManager.getInstance().getFileTypeByFile(virtualFile);
      if (fileType.isBinary()
          || fileType == StdFileTypes.GUI_DESIGNER_FORM
          || fileType == StdFileTypes.IDEA_MODULE
          || fileType == StdFileTypes.IDEA_PROJECT
          || fileType == StdFileTypes.IDEA_WORKSPACE
          || fileType == StdFileTypes.PATCH
          || fileType == StdFileTypes.PROPERTIES

          || fileType == StdFileTypes.XML
          || fileType == StdFileTypes.JSPX && fileType != FileTypes.PLAIN_TEXT // in community tests JSPX==TEXT
        ) {
        enabled = false;
      }
    }
    return enabled;
  }

  @Nullable("returns null if charset set cannot be determined from content")
  public static Charset cachedCharsetFromContent(final VirtualFile virtualFile) {
    if (virtualFile == null) return null;
    final Document document = FileDocumentManager.getInstance().getDocument(virtualFile);
    if (document == null) return null;

    return EncodingManager.getInstance().getCachedCharsetFromContent(document);
  }

  @NotNull
  protected DefaultActionGroup createPopupActionGroup(final JComponent button) {
    return createGroup(true);
  }

  private void fillCharsetActions(DefaultActionGroup group, final VirtualFile virtualFile, List<Charset> charsets) {
    for (Charset slave : charsets) {
      ChangeFileEncodingTo action = new ChangeFileEncodingTo(virtualFile, slave){
        protected void chosen(final VirtualFile file, final Charset charset) {
          ChooseFileEncodingAction.this.chosen(file, charset);
        }
      };
      group.add(action);
    }
  }

  // returns (action text, enabled flag)
  @NotNull
  public static Pair<String,Boolean> update(@Nullable VirtualFile virtualFile) {
    String pattern;
    boolean enabled = virtualFile != null && isEnabled(virtualFile);
    Charset charsetFromContent = cachedCharsetFromContent(virtualFile);
    if (virtualFile != null && FileDocumentManager.getInstance().isFileModified(virtualFile)) {
      //no sense to reload file with UTF-detected chars using other encoding
      if (charsetFromContent != null) {
        pattern = "Encoding (content-specified): {0}";
        enabled = false;
      }
      else if (enabled) {
        pattern = "Save ''{0}''-encoded file in";
      }
      else {
        pattern = "Encoding ''{0}''";
      }
    }
    else {
      // try to reload
      // no sense in reloading file with UTF-detected chars using other encoding
      if (virtualFile != null && LoadTextUtil.wasCharsetDetectedFromBytes(virtualFile)) {
        pattern = "Encoding (auto-detected): {0}";
        enabled = false;
      }
      else if (enabled) {
        pattern = "Reload ''{0}''-encoded file in";
      }
      else if (charsetFromContent != null) {
        pattern = "Encoding (content-specified): {0}";
      }
      else {
        pattern = "Encoding ''{0}''";
      }
    }

    Charset charset = charsetFromContent != null ? charsetFromContent : virtualFile != null ? virtualFile.getCharset() : NO_ENCODING;
    String text = charset == NO_ENCODING ? "Change file encoding" : MessageFormat.format(pattern, charset.displayName());

    return Pair.create(text, enabled);
  }

  private class ClearThisFileEncodingAction extends AnAction {
    private final VirtualFile myFile;

    private ClearThisFileEncodingAction(@Nullable VirtualFile file) {
      super("<Clear>", "Clear " +
                       (file == null ? "default" : "file '"+file.getName()+"'") +
                       " encoding.", null);
      myFile = file;
    }

    public void actionPerformed(final AnActionEvent e) {
      chosen(myFile, NO_ENCODING);
    }
  }

  public static final Charset NO_ENCODING = new Charset("NO_ENCODING", null) {
    public boolean contains(final Charset cs) {
      return false;
    }

    public CharsetDecoder newDecoder() {
      return null;
    }

    public CharsetEncoder newEncoder() {
      return null;
    }
  };
  protected abstract void chosen(VirtualFile virtualFile, Charset charset);

  public DefaultActionGroup createGroup(boolean showClear) {
    DefaultActionGroup group = new DefaultActionGroup();
    List<Charset> favorites = new ArrayList<Charset>(EncodingManager.getInstance().getFavorites());
    Collections.sort(favorites);
    Charset current = myVirtualFile == null ? null : myVirtualFile.getCharset();
    favorites.remove(current);

    if (showClear) {
      group.add(new ClearThisFileEncodingAction(myVirtualFile));
    }
    if (favorites.isEmpty() && !showClear) {
      fillCharsetActions(group, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()));
    }
    else {
      fillCharsetActions(group, myVirtualFile, favorites);

      DefaultActionGroup more = new DefaultActionGroup("more", true);
      group.add(more);
      fillCharsetActions(more, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()));
    }
    return group;
  }
}
