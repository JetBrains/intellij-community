// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcsUtil;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.util.ui.JBUI;
import com.intellij.util.ui.UIUtil;
import com.intellij.xml.util.XmlStringUtil;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;

public final class UIVcsUtil {
  private UIVcsUtil() {
  }

  @SuppressWarnings("unused") // Required for compatibility with external plugins.
  public static JPanel errorPanel(@NotNull @Nls String text, boolean isError) {
    final JLabel label = new JLabel(XmlStringUtil.wrapInHtml(escapeXmlAndAddBr(text)));
    label.setForeground(isError ? SimpleTextAttributes.ERROR_ATTRIBUTES.getFgColor() : UIUtil.getInactiveTextColor());
    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(label, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBUI.insets(1), 0, 0));
    return wrapper;
  }

  public static JPanel infoPanel(@NotNull @Nls String header, @Nls @NotNull String text) {
    //noinspection HardCodedStringLiteral
    final JLabel label = new JLabel(XmlStringUtil.wrapInHtml(
      "<h4>" + StringUtil.escapeXmlEntities(header) + "</h4>" + escapeXmlAndAddBr(text)));
    final JPanel wrapper = new JPanel(new GridBagLayout());
    wrapper.add(label, new GridBagConstraints(0, 0, 1, 1, 0, 0, GridBagConstraints.CENTER, GridBagConstraints.NONE, JBUI.insets(1), 0, 0));
    return wrapper;
  }

  @Nls
  private static String escapeXmlAndAddBr(@Nls @NotNull String text) {
    String escaped = StringUtil.escapeXmlEntities(text);
    //noinspection HardCodedStringLiteral
    escaped = StringUtil.replace(escaped, "\n", UIUtil.BR);
    return escaped;
  }
}
