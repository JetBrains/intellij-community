// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.updateSettings.impl;

import com.intellij.CommonBundle;
import com.intellij.ide.IdeBundle;
import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.options.ShowSettingsUtil;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.ui.ColorUtil;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.Nls;
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
    setTitle(IdeBundle.message("updates.dialog.title", ApplicationNamesInfo.getInstance().getFullProductName()));
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
                                      @NotNull @Nls String messageBody,
                                      @Nullable HyperlinkListener listener) {
    HtmlChunk. Element html = new HtmlBuilder()
      .append(HtmlChunk.head()
                .addRaw(UIUtil.getCssFontDeclaration(UIUtil.getLabelFont()))
                .child(HtmlChunk.styleTag("body {background: #" + ColorUtil.toHex(UIUtil.getPanelBackground()) + ";}")))
      .append(HtmlChunk.body().addRaw(messageBody))
      .wrapWith("html");

    area.setBackground(UIUtil.getPanelBackground());
    area.setBorder(JBUI.Borders.empty());
    area.setText(html.toString());
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
