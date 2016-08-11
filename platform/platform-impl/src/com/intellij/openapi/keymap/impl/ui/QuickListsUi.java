/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.DocumentAdapter;
import com.intellij.util.PairProcessor;
import com.intellij.util.ui.ListItemEditor;
import com.intellij.util.ui.ListModelEditor;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import java.awt.*;
import java.util.List;

class QuickListsUi implements ConfigurableUi<List<QuickList>> {
  public static final String EMPTY = "empty";
  public static final String PANEL = "panel";
  private final KeymapListener keymapListener;

  private final ListItemEditor<QuickList> itemEditor = new ListItemEditor<QuickList>() {
    @NotNull
    @Override
    public Class<QuickList> getItemClass() {
      return QuickList.class;
    }

    @Override
    public QuickList clone(@NotNull QuickList item, boolean forInPlaceEditing) {
      return new QuickList(item.getName(), item.getDescription(), item.getActionIds());
    }

    @Override
    public boolean isEmpty(@NotNull QuickList item) {
      return item.getName().isEmpty() && item.getDescription() == null && item.getActionIds().length == 0;
    }

    @NotNull
    @Override
    public String getName(@NotNull QuickList item) {
      return item.getName();
    }

    @Override
    public boolean isRemovable(@NotNull QuickList item) {
      return QuickListsManager.getInstance().getSchemeManager().isMetadataEditable(item);
    }
  };

  private final ListModelEditor<QuickList> editor = new ListModelEditor<>(itemEditor);

  private JComponent component;
  private final QuickListPanel itemPanel;
  private final JPanel itemPanelWrapper;

  public QuickListsUi() {
    keymapListener = ApplicationManager.getApplication().getMessageBus().syncPublisher(KeymapListener.CHANGE_TOPIC);

    final CardLayout cardLayout = new CardLayout();

    // doesn't make any sense (and in any case scheme manager cannot preserve order)
    editor.disableUpDownActions();
    editor.getList().addListSelectionListener(new ListSelectionListener() {
      @Override
      public void valueChanged(ListSelectionEvent e) {
        QuickList item = editor.getSelected();
        if (item == null) {
          cardLayout.show(itemPanelWrapper, EMPTY);
          itemPanel.setItem(null);
        }
        else {
          cardLayout.show(itemPanelWrapper, PANEL);
          itemPanel.setItem(editor.getMutable(item));
        }
      }
    });

    itemPanel = new QuickListPanel(editor.getModel());
    itemPanel.myName.getDocument().addDocumentListener(new DocumentAdapter() {
      @Override
      protected void textChanged(DocumentEvent e) {
        QuickList item = itemPanel.item;
        if (item != null) {
          String name = itemPanel.myName.getText();
          boolean changed = !item.getName().equals(name);
          item.setName(name);
          if (changed) {
            editor.getList().repaint();
          }
        }
      }
    });

    itemPanelWrapper = new JPanel(cardLayout);

    JLabel descLabel =
      new JLabel("<html>Quick Lists allow you to define commonly used groups of actions (for example, refactoring or VCS actions)" +
                 " and to assign keyboard shortcuts to such groups.</html>");
    descLabel.setBorder(new EmptyBorder(0, 25, 0, 25));

    itemPanelWrapper.add(descLabel, EMPTY);
    itemPanelWrapper.add(itemPanel.getPanel(), PANEL);

    Splitter splitter = new Splitter(false, 0.3f);
    splitter.setFirstComponent(editor.createComponent());
    splitter.setSecondComponent(itemPanelWrapper);
    component = splitter;
  }

  @Override
  public void reset(@NotNull List<QuickList> settings) {
    editor.reset(settings);
  }

  @Override
  public boolean isModified(@NotNull List<QuickList> settings) {
    itemPanel.apply();
    return editor.isModified();
  }

  @Override
  public void apply(@NotNull List<QuickList> settings) throws ConfigurationException {
    itemPanel.apply();

    editor.ensureNonEmptyNames("Quick list should have non empty name");
    editor.processModifiedItems((newItem, oldItem) -> {
      if (!oldItem.getName().equals(newItem.getName())) {
        keymapListener.quickListRenamed(oldItem, newItem);
      }
      return true;
    });

    if (isModified(settings)) {
      java.util.List<QuickList> result = editor.apply();
      keymapListener.processCurrentKeymapChanged(result.toArray(new QuickList[result.size()]));
      QuickListsManager.getInstance().setQuickLists(result);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return component;
  }
}
