// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.structuralsearch.plugin.ui;

import com.intellij.structuralsearch.MatchOptions;
import com.intellij.structuralsearch.plugin.replace.ui.ReplaceConfiguration;
import com.intellij.ui.SimpleListCellRenderer;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

import static com.intellij.openapi.util.text.StringUtil.collapseWhiteSpace;
import static com.intellij.openapi.util.text.StringUtil.shortenTextWithEllipsis;

/**
 * @author Bas Leijdekkers
 */
public class ConfigurationCellRenderer extends SimpleListCellRenderer<Configuration> {

  @Override
  public void customize(@NotNull JList<? extends Configuration> list,
                        Configuration value,
                        int index,
                        boolean selected,
                        boolean hasFocus) {
    final MatchOptions matchOptions = value.getMatchOptions();
    setIcon(matchOptions.getFileType().getIcon());
    if (value instanceof ReplaceConfiguration) {
      setText(shortenTextWithEllipsis(collapseWhiteSpace(matchOptions.getSearchPattern()), 49, 0, true)
              + " ⇒ "
              + shortenTextWithEllipsis(collapseWhiteSpace(value.getReplaceOptions().getReplacement()), 49, 0, true));
    }
    else {
      setText(shortenTextWithEllipsis(collapseWhiteSpace(matchOptions.getSearchPattern()), 100, 0, true));
    }
    setEnabled(list.isEnabled());
  }
}
