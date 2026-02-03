// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.encoding;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TreeUIHelper;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;

@ApiStatus.Internal
public final class ChooseEncodingDialog extends DialogWrapper {
  private final Charset[] myCharsets;
  private final Charset myDefaultCharset;
  private JList myList;
  private JPanel myPanel;

  private ChooseEncodingDialog(final Charset[] charsets, final Charset defaultCharset, final VirtualFile virtualFile) {
    super(false);
    myCharsets = charsets;
    myDefaultCharset = defaultCharset;
    setTitle(IdeBundle.message("dialog.title.choose.encoding.for.the.0", virtualFile.getName()));
    init();
  }

  @Override
  protected @Nullable JComponent createCenterPanel() {
    AbstractListModel model = new AbstractListModel() {
      @Override
      public int getSize() {
        return myCharsets.length;
      }

      @Override
      public Object getElementAt(int i) {
        return myCharsets[i];
      }
    };
    myList.setModel(model);
    TreeUIHelper.getInstance().installListSpeedSearch(myList);
    myList.setCellRenderer(new DefaultListCellRenderer(){
      @Override
      public Component getListCellRendererComponent(final JList list,
                                                    final Object value,
                                                    final int index, final boolean isSelected, final boolean cellHasFocus) {
        Component component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
        Charset charset = (Charset)value;
        //noinspection HardCodedStringLiteral
        setText(charset.displayName());
        return component;
      }
    });
    if (myDefaultCharset != null) {
      myList.setSelectedValue(myDefaultCharset, true);
    }
    return myPanel;
  }

  private Charset getChosen() {
    return (Charset)myList.getSelectedValue();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  protected @NonNls String getDimensionServiceKey() {
    return "#com.intellij.openapi.vfs.encoding.ChooseEncodingDialog";
  }
  
}
