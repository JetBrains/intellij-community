package org.jetbrains.android.uipreview;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.android.util.AndroidBundle;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;

/**
 * @author Eugene.Kudelevsky
 */
class EditDeviceForm {
  private JPanel myContentPanel;
  private JTextField myNameField;
  private JTextField myXdpiField;
  private JTextField myYdpiField;

  public void reset(@NotNull LayoutDevice device) {
    myNameField.setText(device.getName());
    
    final float xDpi = device.getXDpi();
    final float yDpi = device.getYDpi();
    
    myXdpiField.setText(Float.isNaN(xDpi) ? "" : Float.toString(xDpi));
    myYdpiField.setText(Float.isNaN(yDpi) ? "" : Float.toString(yDpi));
  }

  @NotNull
  public JPanel getContentPanel() {
    return myContentPanel;
  }
  
  @NotNull
  public String getName() {
    return myNameField.getText();
  }
  
  public float getXdpi() {
    final String xdpiText = myXdpiField.getText();
    try {
      return Float.parseFloat(xdpiText);
    }
    catch (NumberFormatException e) {
      return Float.NaN;
    }
  }
  
  public float getYdpi() {
    final String ydpiText = myYdpiField.getText();
    try {
      return Float.parseFloat(ydpiText);
    }
    catch (NumberFormatException e) {
      return Float.NaN;
    }
  }
  
  @NotNull
  public DialogWrapper createDialog(@NotNull Project project) {
    return new DialogWrapper(project, false) {
      {
        setTitle(AndroidBundle.message("android.layout.preview.edit.device.dialog.title"));
        init();
      }

      @Override
      protected JComponent createCenterPanel() {
        return myContentPanel;
      }

      @Override
      public JComponent getPreferredFocusedComponent() {
        return myNameField;
      }
    };
  }

  public JTextField getNameField() {
    return myNameField;
  }

  public JTextField getXdpiField() {
    return myXdpiField;
  }

  public JTextField getYdpiField() {
    return myYdpiField;
  }
}
