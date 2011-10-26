/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.ui.TreeUIHelper;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.nio.charset.Charset;

/**
 * @author cdr
*/
public class ChooseEncodingDialog extends DialogWrapper {
  private final Charset[] myCharsets;
  private final Charset myDefaultCharset;
  private JList myList;
  private JPanel myPanel;

  protected ChooseEncodingDialog(final Charset[] charsets, final Charset defaultCharset, final VirtualFile virtualFile) {
    super(false);
    myCharsets = charsets;
    myDefaultCharset = defaultCharset;
    setTitle("Choose Encoding for the '"+virtualFile.getName()+"'");
    init();
  }

  @Override
  @Nullable
  protected JComponent createCenterPanel() {
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
        setText(charset.displayName());
        return component;
      }
    });
    if (myDefaultCharset != null) {
      myList.setSelectedValue(myDefaultCharset, true);
    }
    return myPanel;
  }

  protected Charset getChosen() {
    return (Charset)myList.getSelectedValue();
  }

  @Override
  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @Override
  @NonNls
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.vfs.encoding.ChooseEncodingDialog";
  }
  
}
