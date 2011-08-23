package org.jetbrains.plugins.gradle.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.Named;
import org.jetbrains.plugins.gradle.importing.wizard.adjust.GradleProjectSettingsBuilder;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

/**
 * Holds utility methods for Gradle plugin UI processing.
 * 
 * @author Denis Zhdanov
 * @since 8/23/11 4:26 PM
 */
public class GradleUiUtil {

  private GradleUiUtil() {
  }

  /**
   * Setups given builder to expose controls for management of the given component's name.
   * 
   * @param builder    target settings builder
   * @param component  component which name management should be exposed
   */
  public static void configureNameControl(@NotNull GradleProjectSettingsBuilder builder, @NotNull final Named component) {
    final JTextField nameField = new JTextField();
    builder.add("gradle.import.structure.settings.label.name", nameField);
    nameField.setText(component.getName());
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
        component.setName(nameField.getText());
      }
    });
  }
}
