// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea.ui;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.ui.ListCellRendererWrapper;
import org.zmlx.hg4idea.repo.HgRepository;

import javax.swing.*;

public class HgRepositoryComboboxListCellRenderer extends ListCellRendererWrapper<HgRepository> {
  @Override
  public void customize(JList list, HgRepository value, int index, boolean selected, boolean hasFocus) {
    setText(DvcsUtil.getShortRepositoryName(value));
  }

}