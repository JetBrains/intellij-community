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
package com.intellij.vcs.log.ui.frame;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.JBPopupFactory;
import com.intellij.ui.ListUtil;
import com.intellij.ui.ScrollPaneFactory;
import com.intellij.ui.components.JBList;
import com.intellij.ui.speedSearch.ListWithFilter;
import com.intellij.util.Function;
import com.intellij.util.ui.UIUtil;
import com.intellij.vcs.log.RefGroup;
import com.intellij.vcs.log.VcsRef;
import com.intellij.vcs.log.ui.VcsLogUiImpl;
import com.intellij.vcs.log.ui.render.VcsRefPainter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

class ReferencePopupBuilder {
  @NotNull private final JBPopup myPopup;
  @NotNull private final JBList myList;
  @NotNull private final VcsLogUiImpl myUi;
  @NotNull private final SingleReferenceComponent myRendererComponent;
  @NotNull private final ListCellRenderer myCellRenderer;

  ReferencePopupBuilder(@NotNull RefGroup group, @NotNull VcsLogUiImpl ui) {
    myUi = ui;

    myRendererComponent = new SingleReferenceComponent(new VcsRefPainter(ui.getColorManager(), false));
    myCellRenderer = new ListCellRenderer() {
      @Override
      public Component getListCellRendererComponent(JList list, Object value, int index, boolean isSelected, boolean cellHasFocus) {
        myRendererComponent.setReference((VcsRef)value);
        myRendererComponent.setSelected(isSelected);
        return myRendererComponent;
      }
    };

    myList = createList(group);
    myPopup = createPopup();
  }

  private JBList createList(RefGroup group) {
    JBList list = new JBList(createListModel(group));
    list.setCellRenderer(myCellRenderer);
    ListUtil.installAutoSelectOnMouseMove(list);
    list.setSelectedIndex(0);
    list.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);

    jumpOnMouseClick(list);
    jumpOnEnter(list);

    return list;
  }

  private void jumpOnMouseClick(JBList list) {
    list.addMouseListener(new MouseAdapter() {
      @Override
      public void mouseClicked(MouseEvent e) {
        jumpToSelectedRef();
      }
    });
  }

  private void jumpOnEnter(JBList list) {
    list.addKeyListener(new KeyAdapter() {
      @Override
      public void keyTyped(KeyEvent e) {
        if (e.getKeyCode() == KeyEvent.VK_ENTER) {
          jumpToSelectedRef();
        }
      }
    });
  }

  private JBPopup createPopup() {
    return JBPopupFactory.getInstance()
      .createComponentPopupBuilder(ListWithFilter.wrap(myList, ScrollPaneFactory.createScrollPane(myList), new Function<VcsRef, String>() {
                                     @Override
                                     public String fun(VcsRef vcsRef) {
                                       return vcsRef.getName();
                                     }
                                   }), myList).
      setCancelOnClickOutside(true).
      setCancelOnWindowDeactivation(true).
      setFocusable(true).
      setRequestFocus(true).
      setResizable(true).
      setDimensionServiceKey(myUi.getProject(), "Vcs.Log.Branch.Panel.RefGroup.Popup", false).
      createPopup();
  }

  private static DefaultListModel createListModel(RefGroup group) {
    DefaultListModel model = new DefaultListModel();
    for (final VcsRef vcsRef : group.getRefs()) {
      model.addElement(vcsRef);
    }
    return model;
  }

  @NotNull
  JBPopup getPopup() {
    return myPopup;
  }

  private void jumpToSelectedRef() {
    myPopup.cancel(); // close the popup immediately not to stay at the front if jumping to a commits takes long time.
    VcsRef selectedRef = (VcsRef)myList.getSelectedValue();
    if (selectedRef != null) {
      myUi.jumpToCommit(selectedRef.getCommitHash(), selectedRef.getRoot());
    }
  }

  private static class SingleReferenceComponent extends JPanel {
    private static final int PADDING_Y = 2;
    private static final int PADDING_X = 5;
    @NotNull private final VcsRefPainter myReferencePainter;

    @Nullable private VcsRef myReference;
    public boolean mySelected;

    public SingleReferenceComponent(@NotNull VcsRefPainter referencePainter) {
      myReferencePainter = referencePainter;
    }

    @Override
    protected void paintComponent(Graphics g) {
      g.setColor(mySelected ? UIUtil.getListSelectionBackground() : UIUtil.getListBackground());
      g.fillRect(0, 0, getWidth(), getHeight());

      if (myReference != null) {
        myReferencePainter.paint(myReference, g, PADDING_X, PADDING_Y);
      }
    }

    @Override
    public Dimension getPreferredSize() {
      if (myReference == null) return super.getPreferredSize();
      Dimension size = myReferencePainter.getSize(myReference, this);
      return new Dimension(size.width + 2 * PADDING_X, size.height + 2 * PADDING_Y);
    }

    public void setReference(@NotNull VcsRef reference) {
      myReference = reference;
    }

    public void setSelected(boolean selected) {
      mySelected = selected;
    }
  }
}
