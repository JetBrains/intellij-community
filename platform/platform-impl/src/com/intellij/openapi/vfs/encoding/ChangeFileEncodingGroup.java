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
package com.intellij.openapi.vfs.encoding;

import com.intellij.openapi.actionSystem.*;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author cdr
 */
public class ChangeFileEncodingGroup extends ActionGroup {
  @NotNull
  public AnAction[] getChildren(@Nullable final AnActionEvent e) {
    if (e == null) return EMPTY_ARRAY;
    VirtualFile virtualFile = e.getData(PlatformDataKeys.VIRTUAL_FILE);
    if(virtualFile == null || !virtualFile.isInLocalFileSystem()){
      return EMPTY_ARRAY;
    }

    List<Charset> charsets = new ArrayList<Charset>(EncodingManager.getInstance().getFavorites());
    Collections.sort(charsets);
    Charset current = virtualFile.getCharset();
    charsets.remove(current);

    List<AnAction> children = new ArrayList<AnAction>(charsets.size());
    for (Charset charset : charsets) {
      ChangeFileEncodingTo action = new ChangeFileEncodingTo(virtualFile, charset);
      children.add(action);
    }

    children.add(new More(virtualFile));
    children.add(new Separator());
    return children.toArray(new AnAction[children.size()]);
  }

  private static class More extends AnAction implements DumbAware {
    private final VirtualFile myVirtualFile;

    private More(VirtualFile virtualFile) {
      myVirtualFile = virtualFile;
      getTemplatePresentation().setText("more...");
    }

    public void actionPerformed(final AnActionEvent e) {
      Charset[] charsets = CharsetToolkit.getAvailableCharsets();

      ChooseEncodingDialog dialog = new ChooseEncodingDialog(charsets, myVirtualFile.getCharset(), myVirtualFile);
      dialog.show();
      Charset charset = dialog.getChosen();
      if (dialog.isOK() && charset != null) {
        EncodingManager.getInstance().setEncoding(myVirtualFile, charset);
      }
    }
  }
}
