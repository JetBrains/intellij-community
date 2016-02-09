/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.util.ui;

import com.intellij.openapi.util.Getter;
import com.intellij.ui.PopupMenuListenerAdapter;
import com.intellij.ui.TextFieldWithHistory;
import com.intellij.ui.TextFieldWithHistoryWithBrowseButton;
import com.intellij.util.PairConvertor;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.Convertor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.event.PopupMenuEvent;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.ItemEvent;
import java.awt.event.ItemListener;
import java.util.List;

/**
 * @author Irina.Chernushina on 1/5/2016.
 */
public interface ReadonlyFieldWithHistoryWithBrowseButton {
  JComponent getComponent();
  void set(@NotNull String text);
  @NotNull String get();
  void addListener(Runnable listener);
  void setPreferredWidthToFitText();

  class Builder {
    private PairConvertor<ActionEvent, String, String> myActionListener;
    private Getter<List<String>> myHistoryProvider;
    private Convertor<TextFieldWithHistory, ListCellRenderer> myRendererCreator;

    public Builder withRenderer(@NotNull final Convertor<TextFieldWithHistory, ListCellRenderer> convertor) {
      myRendererCreator = convertor;
      return this;
    }

    public Builder withHistoryProvider(@NotNull final Getter<List<String>> provider) {
      myHistoryProvider = provider;
      return this;
    }

    public Builder withActionListener(@NotNull final PairConvertor<ActionEvent, String, String> listener) {
      myActionListener = listener;
      return this;
    }

    public ReadonlyFieldWithHistoryWithBrowseButton build() {
      final TextFieldWithHistoryWithBrowseButton field = new TextFieldWithHistoryWithBrowseButton();
      final TextFieldWithHistory textFieldWithHistory = field.getChildComponent();
      textFieldWithHistory.setHistorySize(-1);
      textFieldWithHistory.setMinimumAndPreferredWidth(0);
      textFieldWithHistory.setEditable(false);

      final ReadonlyFieldWithHistoryWithBrowseButton wrapper = createReadonlyFieldWrapper(field);

      if (myActionListener != null) {
        field.getButton().addActionListener(new ActionListener() {
          @Override
          public void actionPerformed(ActionEvent e) {
            final String value = myActionListener.convert(e, wrapper.get());
            if (value != null) {
              wrapper.set(value);
            }
          }
        });
      }
      if (myHistoryProvider != null) {
        textFieldWithHistory.addPopupMenuListener(new PopupMenuListenerAdapter() {
          @Override
          public void popupMenuWillBecomeVisible(PopupMenuEvent e) {
            SwingHelper.setHistory(textFieldWithHistory, ContainerUtil.notNullize(myHistoryProvider.get()), false);
          }
        });
      }
      if (myRendererCreator != null) {
        final ListCellRenderer renderer = myRendererCreator.convert(textFieldWithHistory);
        if (renderer != null) {
          //noinspection GtkPreferredJComboBoxRenderer
          textFieldWithHistory.setRenderer(renderer);
        }
      }
      return wrapper;
    }

    @NotNull
    public static ReadonlyFieldWithHistoryWithBrowseButton createReadonlyFieldWrapper(final TextFieldWithHistoryWithBrowseButton field) {
      return new ReadonlyFieldWithHistoryWithBrowseButton() {
        @Override
        public JComponent getComponent() {
          return field;
        }

        @Override
        public void set(@NotNull String text) {
          final TextFieldWithHistory component = field.getChildComponent();
          if (!component.getHistory().contains(text)) {
            component.setTextAndAddToHistory(text);
          }
          component.setSelectedItem(text);
        }

        @NotNull
        @Override
        public String get() {
          final Object item = field.getChildComponent().getSelectedItem();
          return item == null ? "" : item.toString().trim();
        }

        @Override
        public void addListener(final Runnable listener) {
          field.getChildComponent().addItemListener(new ItemListener() {
            @Override
            public void itemStateChanged(ItemEvent e) {
              listener.run();
            }
          });
        }

        public void setPreferredWidthToFitText() {
          SwingHelper.setPreferredWidthToFitText(field);
        }
      };
    }
  }
}
