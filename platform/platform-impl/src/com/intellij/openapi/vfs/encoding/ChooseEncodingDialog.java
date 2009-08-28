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

  @Nullable
  protected JComponent createCenterPanel() {
    AbstractListModel model = new AbstractListModel() {
      public int getSize() {
        return myCharsets.length;
      }

      public Object getElementAt(int i) {
        return myCharsets[i];
      }
    };
    myList.setModel(model);
    TreeUIHelper.getInstance().installListSpeedSearch(myList);
    myList.setCellRenderer(new DefaultListCellRenderer(){
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

  public JComponent getPreferredFocusedComponent() {
    return myList;
  }

  @NonNls
  protected String getDimensionServiceKey() {
    return "#com.intellij.openapi.vfs.encoding.ChooseEncodingDialog";
  }
  
}
