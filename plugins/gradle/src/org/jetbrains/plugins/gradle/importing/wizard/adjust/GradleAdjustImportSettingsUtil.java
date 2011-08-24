package org.jetbrains.plugins.gradle.importing.wizard.adjust;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.Named;
import org.jetbrains.plugins.gradle.util.GradleBundle;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * @author Denis Zhdanov
 * @since 8/24/11 2:40 PM
 */
public class GradleAdjustImportSettingsUtil {

  private GradleAdjustImportSettingsUtil() {
  }

  /**
   * Setups given builder to expose controls for management of the given component's name.
   *
   * @param builder    target settings builder
   * @param component  component which name management should be exposed
   * @return           label that should be used for reporting errors within the given component's name
   */
  public static JLabel configureNameControl(@NotNull GradleProjectSettingsBuilder builder, @NotNull final Named component) {
    final JTextField nameField = new JTextField();
    nameField.setText(component.getName());
    final JLabel result = builder.add("gradle.import.structure.settings.label.name", nameField);
    nameField.getDocument().addDocumentListener(new DocumentListener() {
      @Override
      public void insertUpdate(DocumentEvent e) {
        applyNewName();
      }
      @Override
      public void removeUpdate(DocumentEvent e) {
        applyNewName();
      }
      @Override
      public void changedUpdate(DocumentEvent e) {
      }
      private void applyNewName() {
        String text = nameField.getText();
        if (text == null) {
          return;
        }
        component.setName(text.trim());
        if (result.isVisible()) {
          result.setVisible(false);
        }
      }
    });
    return result;
  }

  /**
   * Performs generic check of the name of the given component.
   * 
   * @param namedComponent  target component
   * @param errorLabel      storage for the validation error message
   * @return                <code>true</code> if validation is successful; <code>false</code> otherwise
   */
  public static boolean validate(@NotNull Named namedComponent, @NotNull JLabel errorLabel) {
    if (!StringUtil.isEmptyOrSpaces(namedComponent.getName())) {
      return true;
    }
    errorLabel.setText(GradleBundle.message("gradle.import.text.error.undefined.name"));
    errorLabel.setVisible(true);
    return false;
  }
}
