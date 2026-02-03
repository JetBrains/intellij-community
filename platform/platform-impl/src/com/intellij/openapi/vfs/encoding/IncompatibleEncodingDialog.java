// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.nio.charset.Charset;

@ApiStatus.Internal
public final class IncompatibleEncodingDialog extends DialogWrapper {
  private final @NotNull VirtualFile virtualFile;
  private final @NotNull Charset charset;
  private final @NotNull EncodingUtil.Magic8 safeToReload;
  private final @NotNull EncodingUtil.Magic8 safeToConvert;

  IncompatibleEncodingDialog(@NotNull VirtualFile virtualFile,
                             final @NotNull Charset charset,
                             @NotNull EncodingUtil.Magic8 safeToReload,
                             @NotNull EncodingUtil.Magic8 safeToConvert) {
    super(false);
    this.virtualFile = virtualFile;
    this.charset = charset;
    this.safeToReload = safeToReload;
    this.safeToConvert = safeToConvert;
    setTitle(IdeBundle.message("reload.or.convert.dialog.title", virtualFile.getName(), charset.displayName()));
    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    JLabel label = new JLabel(XmlStringUtil.wrapInHtml(
      IdeBundle.message("dialog.message.incompatible.encoding", charset.displayName(), virtualFile.getName())));
    label.setIcon(Messages.getQuestionIcon());
    label.setIconTextGap(10);
    return label;
  }

  @Override
  protected Action @NotNull [] createActions() {
    DialogWrapperAction reloadAction = new DialogWrapperAction(IdeBundle.message("button.reload")) {
      @Override
      protected void doAction(ActionEvent e) {
        if (safeToReload == EncodingUtil.Magic8.NO_WAY) {
          Ref<Charset> current = Ref.create();
          EncodingUtil.FailReason failReason = EncodingUtil.checkCanReload(virtualFile, current);
          int res;
          byte[] bom = virtualFile.getBOM();
          String explanation = "<br><br>" +
                               (failReason == null ? "" : "Why: " + EncodingUtil.reasonToString(failReason, virtualFile) + "<br>") +
                               (current.isNull() ? "" : "Current encoding: '" + current.get().displayName() + "'");
          if (bom != null) {
            Messages.showErrorDialog(XmlStringUtil.wrapInHtml(
              IdeBundle.message("dialog.title.file.0.can.t.be.reloaded", virtualFile.getName(), charset.displayName(), explanation)),
                                     IdeBundle.message("incompatible.encoding.dialog.title", charset.displayName()));
            res = -1;
          }
          else {
            res = Messages.showDialog(XmlStringUtil.wrapInHtml(
              IdeBundle.message("dialog.title.file.0.most.likely.isn.t.stored", virtualFile.getName(), charset.displayName(), explanation)),
                                      IdeBundle.message("incompatible.encoding.dialog.title", charset.displayName()),
                                      new String[]{IdeBundle.message("button.reload.anyway"), CommonBundle.getCancelButtonText()}, 1,
                                      AllIcons.General.WarningDialog);
          }
          if (res != 0) {
            doCancelAction();
            return;
          }
        }
        close(RELOAD_EXIT_CODE);
      }
    };
    if (!SystemInfo.isMac && safeToReload == EncodingUtil.Magic8.NO_WAY) {
      reloadAction.putValue(Action.SMALL_ICON, AllIcons.General.Warning);
    }
    DialogWrapperAction convertAction = new DialogWrapperAction(IdeBundle.message("button.convert")) {
      @Override
      protected void doAction(ActionEvent e) {
        if (safeToConvert == EncodingUtil.Magic8.NO_WAY) {
          EncodingUtil.FailReason error = EncodingUtil.checkCanConvert(virtualFile);
          int res = Messages.showDialog(
            XmlStringUtil.wrapInHtml(
              IdeBundle.message("encoding.do.not.convert.message", charset.displayName()) + "<br><br>" +
              (error == null
               ? IdeBundle.message("encoding.unsupported.characters.message", charset.displayName())
               : EncodingUtil.reasonToString(error, virtualFile))),
            IdeBundle.message("incompatible.encoding.dialog.title", charset.displayName()),
            new String[]{IdeBundle.message("button.convert.anyway"), CommonBundle.getCancelButtonText()}, 1,
            AllIcons.General.WarningDialog);
          if (res != 0) {
            doCancelAction();
            return;
          }
        }
        close(CONVERT_EXIT_CODE);
      }

      @Override
      public boolean isEnabled() {
        return !VirtualFileUtil.isTooLarge(virtualFile);
      }
    };
    if (!SystemInfo.isMac && safeToConvert == EncodingUtil.Magic8.NO_WAY) {
      convertAction.putValue(Action.SMALL_ICON, AllIcons.General.Warning);
    }
    Action cancelAction = getCancelAction();
    cancelAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    return new Action[]{reloadAction, convertAction, cancelAction};
  }

  static final int RELOAD_EXIT_CODE = 10;
  static final int CONVERT_EXIT_CODE = 20;
}
