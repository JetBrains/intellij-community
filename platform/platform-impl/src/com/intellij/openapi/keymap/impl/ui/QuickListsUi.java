// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl.ui;

import com.intellij.ide.IdeBundle;
import com.intellij.openapi.actionSystem.ex.QuickList;
import com.intellij.openapi.actionSystem.ex.QuickListsManager;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.options.ConfigurableUi;
import com.intellij.openapi.options.ConfigurationException;
import com.intellij.openapi.ui.Splitter;
import com.intellij.ui.DocumentAdapter;
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

final class QuickListsUi implements ConfigurableUi<List<QuickList>> {
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

  private final JComponent component;
  private final QuickListPanel itemPanel;
  private final JPanel itemPanelWrapper;

  QuickListsUi() {
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
      protected void textChanged(@NotNull DocumentEvent e) {
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

    JLabel descLabel = new JLabel(IdeBundle.message("quick.lists.description"));
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

    editor.ensureNonEmptyNames(IdeBundle.message("quick.lists.not.empty.name"));
    editor.processModifiedItems((newItem, oldItem) -> {
      if (!oldItem.getName().equals(newItem.getName())) {
        keymapListener.quickListRenamed(oldItem, newItem);
      }
      return true;
    });

    if (isModified(settings)) {
      java.util.List<QuickList> result = editor.apply();
      keymapListener.processCurrentKeymapChanged(result.toArray(new QuickList[0]));
      QuickListsManager.getInstance().setQuickLists(result);
    }
  }

  @NotNull
  @Override
  public JComponent getComponent() {
    return component;
  }
}
