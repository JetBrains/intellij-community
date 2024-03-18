// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.options.binding;

import com.intellij.openapi.editor.event.DocumentListener;
import com.intellij.ui.DocumentAdapter;
import com.intellij.ui.EditorTextField;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * @author Dmitry Avdeev
 *
 * @deprecated Use Kotlin UI DSL with bindings
 */
@ApiStatus.Internal
@Deprecated(forRemoval = true)
public abstract class ValueAccessor<V> {

  public abstract V getValue();
  public abstract void setValue(V value);
  public abstract Class<V> getType();

  public static ControlValueAccessor textFieldAccessor(final JTextField from) {
    return new ControlValueAccessor<String>() {
      @Override
      public String getValue() {
        return from.getText();
      }

      @Override
      public void setValue(String value) {
        from.setText(value);
      }

      @Override
      public Class<String> getType() {
        return String.class;
      }

      @Override
      public boolean isEnabled() {
        return from.isEnabled();
      }

      @Override
      public void addChangeListener(final Runnable listener) {
        from.getDocument().addDocumentListener(new DocumentAdapter() {
          @Override
          protected void textChanged(@NotNull DocumentEvent e) {
            listener.run();
          }
        });
      }
    };
  }

  public static ControlValueAccessor editorTextFieldAccessor(final EditorTextField from) {
    return new ControlValueAccessor<String>() {
      @Override
      public String getValue() {
        return from.getText();
      }

      @Override
      public void setValue(String value) {
        from.setText(value);
      }

      @Override
      public Class<String> getType() {
        return String.class;
      }

      @Override
      public boolean isEnabled() {
        return from.isEnabled();
      }

      @Override
      public void addChangeListener(final Runnable listener) {
        from.getDocument().addDocumentListener(new DocumentListener() {
          @Override
          public void documentChanged(@NotNull com.intellij.openapi.editor.event.DocumentEvent event) {
            listener.run();
          }
        });
      }
    };
  }

  public static ControlValueAccessor checkBoxAccessor(final JCheckBox from) {
    return new ControlValueAccessor<Boolean>() {

      @Override
      public Boolean getValue() {
        return from.isSelected();
      }

      @Override
      public void setValue(Boolean value) {
        from.setSelected(value.booleanValue());
      }

      @Override
      public Class<Boolean> getType() {
        return Boolean.class;
      }

      @Override
      public boolean isEnabled() {
        return from.isEnabled();
      }

      @Override
      public void addChangeListener(final Runnable listener) {
        from.addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            listener.run();
          }
        });
      }
    };
  }

}
