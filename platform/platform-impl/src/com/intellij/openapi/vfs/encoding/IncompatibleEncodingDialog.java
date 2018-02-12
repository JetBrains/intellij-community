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
package com.intellij.openapi.vfs.encoding;

import com.intellij.icons.AllIcons;
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
    setTitle(virtualFile.getName() + ": Reload or Convert to "+charset.displayName());
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

  @NotNull
  @Override
  protected Action[] createActions() {
    DialogWrapperAction reloadAction = new DialogWrapperAction("Reload") {
      @Override
      protected void doAction(ActionEvent e) {
        if (safeToReload == EncodingUtil.Magic8.NO_WAY) {
          Ref<Charset> current = Ref.create();
          EncodingUtil.FailReason failReason = EncodingUtil.checkCanReload(virtualFile, current);
          Charset autoDetected = current.get();
          int res;
          byte[] bom = virtualFile.getBOM();
          if (bom != null) {
            Messages
              .showErrorDialog(XmlStringUtil.wrapInHtml(
                          "File '" + virtualFile.getName() + "' can't be reloaded in the '" + charset.displayName() + "' encoding.<br><br>" +
                          (failReason == null ? "" : "Why: "+ failReason +"<br>") +
                          (autoDetected == null ? "" : "Detected encoding: '"+ autoDetected.displayName()+"'")),
                               "Incompatible Encoding: " + charset.displayName()
                          );
            res = -1;
          }
          else {
            res = Messages
              .showDialog(XmlStringUtil.wrapInHtml(
                        "File '" + virtualFile.getName() + "' most likely isn't stored in the '" + charset.displayName() + "' encoding." +
                        "<br><br>" +
                        (failReason == null ? "" : "Why: " + failReason + "<br>") +
                        (autoDetected == null ? "" : "Detected encoding: '" + autoDetected.displayName() + "'")),
                        "Incompatible Encoding: " + charset.displayName(), new String[]{"Reload anyway", "Cancel"}, 1,
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
    DialogWrapperAction convertAction = new DialogWrapperAction("Convert") {
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
            "Incompatible Encoding: " + charset.displayName(), new String[]{"Convert anyway", "Cancel"}, 1,
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
