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
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Function;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
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

  private void fillCharsetActions(@NotNull DefaultActionGroup group,
                                  @Nullable VirtualFile virtualFile,
                                  @NotNull List<Charset> charsets,
                                  @NotNull final Function<Charset, String> charsetFilter) {
    for (final Charset slave : charsets) {
      ChangeFileEncodingTo action = new ChangeFileEncodingTo(virtualFile, slave) {
        {
          String description = charsetFilter.fun(slave);
          if (description == null) {
            getTemplatePresentation().setIcon(AllIcons.General.Warning);
          }
          else {
            getTemplatePresentation().setDescription(description);
          }
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
        }

        @Override
        protected void chosen(final VirtualFile file, @NotNull final Charset charset) {
          ChooseFileEncodingAction.this.chosen(file, charset);
        }
      };
      group.add(action);
    }
  }

  private class ClearThisFileEncodingAction extends AnAction {
    private final VirtualFile myFile;

    private ClearThisFileEncodingAction(@Nullable VirtualFile file, @NotNull String clearItemText) {
      super(clearItemText, "Clear " + (file == null ? "default" : "file '"+file.getName()+"'") + " encoding.", null);
      myFile = file;
    }

    @Override
    public void actionPerformed(@NotNull final AnActionEvent e) {
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
  protected DefaultActionGroup createCharsetsActionGroup(@Nullable("null means do not show 'clear' text") String clearItemText,
                                                      Charset alreadySelected,
                                                      @NotNull Function<Charset, String> charsetFilter) {
    DefaultActionGroup group = new DefaultActionGroup();
    List<Charset> favorites = new ArrayList<>(EncodingManager.getInstance().getFavorites());
    Collections.sort(favorites);
    Charset current = myVirtualFile == null ? null : myVirtualFile.getCharset();
    favorites.remove(current);
    favorites.remove(alreadySelected);

    if (clearItemText != null) {
      group.add(new ClearThisFileEncodingAction(myVirtualFile, clearItemText));
    }
    if (favorites.isEmpty() && clearItemText == null) {
      fillCharsetActions(group, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()), charsetFilter);
    }
    else {
      fillCharsetActions(group, myVirtualFile, favorites, charsetFilter);

      DefaultActionGroup more = new DefaultActionGroup("more", true);
      group.add(more);
      fillCharsetActions(more, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()), charsetFilter);
    }
    return group;
  }
}
