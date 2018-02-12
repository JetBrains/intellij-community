// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.HyperlinkEvent;
import javax.swing.event.HyperlinkListener;
import java.awt.*;

/**
 * @author anna
 */
public abstract class AbstractUpdateDialog extends DialogWrapper {
  private final boolean myEnableLink;

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

  protected String getOkButtonText() {
    return CommonBundle.getOkButtonText();
  }

  protected String getCancelButtonText() {
    return CommonBundle.getCancelButtonText();
  }

  protected void configureMessageArea(@NotNull JEditorPane area) {
    String messageBody = myEnableLink ? IdeBundle.message("updates.configure.label") : "";
    configureMessageArea(area, messageBody, null, null);
  }

  protected void configureMessageArea(@NotNull JEditorPane area,
                                      @NotNull String messageBody,
                                      @Nullable Color fontColor,
                                      @Nullable HyperlinkListener listener) {
    String text =
      "<html><head>" +
      UIUtil.getCssFontDeclaration(UIUtil.getLabelFont(), fontColor, null, null) +
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