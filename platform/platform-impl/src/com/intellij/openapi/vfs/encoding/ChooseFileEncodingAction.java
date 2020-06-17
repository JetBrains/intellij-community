// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vfs.encoding;

import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.ide.lightEdit.LightEditCompatible;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.DefaultActionGroup;
import com.intellij.openapi.actionSystem.ex.ComboBoxAction;
import com.intellij.openapi.fileEditor.impl.LoadTextUtil;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.NotNullLazyValue;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.VolatileNotNullLazyValue;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.IconDeferrer;
import com.intellij.util.ArrayUtilRt;
import com.intellij.util.Function;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public abstract class ChooseFileEncodingAction extends ComboBoxAction {
  private final VirtualFile myVirtualFile;

  protected ChooseFileEncodingAction(@Nullable VirtualFile virtualFile) {
    myVirtualFile = virtualFile;
  }

  @Override
  public abstract void update(@NotNull final AnActionEvent e);

  private void fillCharsetActions(@NotNull DefaultActionGroup group,
                                  @Nullable final VirtualFile virtualFile,
                                  @NotNull List<? extends Charset> charsets,
                                  @NotNull final Function<? super Charset, String> descriptionSupplier) {
    for (final Charset charset : charsets) {
      AnAction action = new CharsetAction(charset.displayName(), null, EmptyIcon.ICON_16) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          chosen(virtualFile, charset);
        }

        @Override
        public void update(@NotNull AnActionEvent e) {
          super.update(e);
          String description = descriptionSupplier.fun(charset);
          Icon defer;
          if (virtualFile == null || virtualFile.isDirectory()) {
            defer = null;
          }
          else {
            NotNullLazyValue<CharSequence> myText = VolatileNotNullLazyValue.createValue(()->LoadTextUtil.loadText(virtualFile));
            NotNullLazyValue<byte[]> myBytes = VolatileNotNullLazyValue.createValue(() -> {
              try {
                return virtualFile.contentsToByteArray();
              }
              catch (IOException e1) {
                return ArrayUtilRt.EMPTY_BYTE_ARRAY;
              }
            });
            defer = IconDeferrer.getInstance().defer(null, Pair.create(virtualFile, charset), pair -> {
              VirtualFile myFile = pair.getFirst();
              Charset charset = pair.getSecond();
              CharSequence text = myText.getValue();
              byte[] bytes = myBytes.getValue();
              EncodingUtil.Magic8 safeToReload = EncodingUtil.isSafeToReloadIn(myFile, text, bytes, charset);
              EncodingUtil.Magic8 safeToConvert;
              if (safeToReload == EncodingUtil.Magic8.ABSOLUTELY) {
                safeToConvert = EncodingUtil.Magic8.ABSOLUTELY;
              }
              else {
                safeToConvert = EncodingUtil.isSafeToConvertTo(myFile, text, bytes, charset);
              }
              return safeToReload == EncodingUtil.Magic8.ABSOLUTELY || safeToConvert == EncodingUtil.Magic8.ABSOLUTELY ? null :
                     safeToReload == EncodingUtil.Magic8.WELL_IF_YOU_INSIST || safeToConvert == EncodingUtil.Magic8.WELL_IF_YOU_INSIST ?
                     AllIcons.General.Warning : AllIcons.General.Error;
            });
          }
          e.getPresentation().setIcon(defer);
          e.getPresentation().setDescription(description);
        }
      };
      group.add(action);
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
  protected DefaultActionGroup createCharsetsActionGroup(@Nullable String clearItemText,
                                                         @Nullable Charset alreadySelected,
                                                         @NotNull Function<? super Charset, String> descriptionSupplier) {
    DefaultActionGroup group = new DefaultActionGroup();
    List<Charset> favorites = new ArrayList<>(EncodingManager.getInstance().getFavorites());
    Collections.sort(favorites);
    Charset current = myVirtualFile == null ? null : myVirtualFile.getCharset();
    favorites.remove(current);
    favorites.remove(alreadySelected);

    if (clearItemText != null) {
      String description = myVirtualFile == null
                           ? IdeBundle.message("action.clear.encoding.description")
                           : IdeBundle.message("action.clear.encoding.description.file", myVirtualFile.getName());
      group.add(new DumbAwareAction(clearItemText, description, null) {
        @Override
        public void actionPerformed(@NotNull AnActionEvent e) {
          chosen(myVirtualFile, NO_ENCODING);
        }
      });
    }
    if (favorites.isEmpty() && clearItemText == null) {
      fillCharsetActions(group, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()), descriptionSupplier);
    }
    else {
      fillCharsetActions(group, myVirtualFile, favorites, descriptionSupplier);

      DefaultActionGroup more = DefaultActionGroup.createPopupGroup(() -> IdeBundle.message("action.text.more"));
      group.add(more);
      fillCharsetActions(more, myVirtualFile, Arrays.asList(CharsetToolkit.getAvailableCharsets()), descriptionSupplier);
    }
    return group;
  }

  private abstract static class CharsetAction extends DumbAwareAction implements LightEditCompatible {
    CharsetAction(String name, String description, Icon icon) {
      super(name, description, icon);
    }
  }
}
