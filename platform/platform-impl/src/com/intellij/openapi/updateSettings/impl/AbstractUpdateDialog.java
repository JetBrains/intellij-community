// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;

/**
 * @author anna
 */
public abstract class AbstractUpdateDialog extends DialogWrapper {
  protected final boolean myEnableLink;

  protected AbstractUpdateDialog(boolean enableLink) {
    super(true);
    myEnableLink = enableLink;
    setTitle(IdeBundle.message("update.notifications.title"));
  }

  @Override
  protected void init() {
    setOKButtonText(getOkButtonText());
    setCancelButtonText(getCancelButtonText());
    super.init();
  }

  protected @NlsContexts.Button String getOkButtonText() {
    return CommonBundle.getOkButtonText();
  }

  protected @NlsContexts.Button String getCancelButtonText() {
    return CommonBundle.getCancelButtonText();
  }

  protected void configureMessageArea(@NotNull JEditorPane area) {
    String messageBody = myEnableLink ? IdeBundle.message("updates.configure.label") : "";
    configureMessageArea(area, messageBody, null);
  }

  protected void configureMessageArea(@NotNull JEditorPane area,
                                      @NotNull String messageBody,
                                      @Nullable HyperlinkListener listener) {
    String text =
      "<html><head>" +
      UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()) +
      "<style>body {background: #" + ColorUtil.toHex(UIUtil.getPanelBackground()) + ";}</style>" +
      "</head><body>" + messageBody + "</body></html>";

    area.setBackground(UIUtil.getPanelBackground());
    area.setBorder(JBUI.Borders.empty());
    area.setText(text);
    area.setCaretPosition(0);
    area.setEditable(false);

    if (listener == null && myEnableLink) {
      listener = (e) -> {
        if (e.getEventType() == HyperlinkEvent.EventType.ACTIVATED) {
          ShowSettingsUtil.getInstance().editConfigurable(area, new UpdateSettingsConfigurable(false));
        }
      };
    }
    if (listener != null) {
      area.addHyperlinkListener(listener);
    }
  }
}