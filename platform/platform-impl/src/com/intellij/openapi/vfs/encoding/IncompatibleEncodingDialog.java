// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vfs.encoding;

import com.intellij.CommonBundle;
import com.intellij.icons.AllIcons;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.Ref;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtilRt;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.event.ActionEvent;
import java.nio.charset.Charset;

public class IncompatibleEncodingDialog extends DialogWrapper {
  @NotNull private final VirtualFile virtualFile;
  @NotNull private final Charset charset;
  @NotNull private final EncodingUtil.Magic8 safeToReload;
  @NotNull private final EncodingUtil.Magic8 safeToConvert;

  IncompatibleEncodingDialog(@NotNull VirtualFile virtualFile,
                             @NotNull final Charset charset,
                             @NotNull EncodingUtil.Magic8 safeToReload,
                             @NotNull EncodingUtil.Magic8 safeToConvert) {
    super(false);
    this.virtualFile = virtualFile;
    this.charset = charset;
    this.safeToReload = safeToReload;
    this.safeToConvert = safeToConvert;
    setTitle(IdeBundle.message("dialog.title.0.reload.or.convert.to.1", virtualFile.getName(), charset.displayName()));
    init();
  }

  @Nullable
  @Override
  protected JComponent createCenterPanel() {
    JLabel label = new JLabel(XmlStringUtil.wrapInHtml(
                              "The encoding you've chosen ('" + charset.displayName() + "') may change the contents of '" + virtualFile.getName() + "'.<br>" +
                              "Do you want to<br>" +
                              "1. <b>Reload</b> the file from disk in the new encoding '" + charset.displayName() + "' and overwrite editor contents or<br>" +
                              "2. <b>Convert</b> the text and overwrite file in the new encoding" + "?"));
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
                               (failReason == null ? "" : "Why: " + failReason + "<br>") +
                               (current.isNull() ? "" : "Current encoding: '" + current.get().displayName() + "'");
          if (bom != null) {
            Messages.showErrorDialog(XmlStringUtil.wrapInHtml(
              IdeBundle.message("dialog.title.file.0.can.t.be.reloaded", virtualFile.getName(), charset.displayName(), explanation)),
                                     IdeBundle.message("dialog.title.incompatible.encoding.0", charset.displayName()));
            res = -1;
          }
          else {
            res = Messages.showDialog(XmlStringUtil.wrapInHtml(
              IdeBundle.message("dialog.title.file.0.most.likely.isn.t.stored", virtualFile.getName(), charset.displayName(), explanation)),
                                      IdeBundle.message("dialog.title.incompatible.encoding.0", charset.displayName()),
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
    reloadAction.putValue(Action.MNEMONIC_KEY, (int)'R');
    DialogWrapperAction convertAction = new DialogWrapperAction(IdeBundle.message("button.convert")) {
      @Override
      protected void doAction(ActionEvent e) {
        if (safeToConvert == EncodingUtil.Magic8.NO_WAY) {
          EncodingUtil.FailReason error = EncodingUtil.checkCanConvert(virtualFile);
          int res = Messages.showDialog(
            XmlStringUtil.wrapInHtml(
              "Please do not convert to '" + charset.displayName() + "'.<br><br>" +
              (error == null
               ? "Encoding '" + charset.displayName() + "' does not support some characters from the text."
               : EncodingUtil.reasonToString(error, virtualFile))),
            IdeBundle.message("dialog.title.incompatible.encoding.0", charset.displayName()),
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
        return !FileUtilRt.isTooLarge(virtualFile.getLength());
      }
    };
    if (!SystemInfo.isMac && safeToConvert == EncodingUtil.Magic8.NO_WAY) {
      convertAction.putValue(Action.SMALL_ICON, AllIcons.General.Warning);
    }
    convertAction.putValue(Action.MNEMONIC_KEY, (int)'C');
    Action cancelAction = getCancelAction();
    cancelAction.putValue(DEFAULT_ACTION, Boolean.TRUE);
    return new Action[]{reloadAction, convertAction, cancelAction};
  }

  static final int RELOAD_EXIT_CODE = 10;
  static final int CONVERT_EXIT_CODE = 20;
}
