/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.util.Comparing;
import com.intellij.util.ui.FontInfo;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author Sergey.Malenkov
 */
public final class FontComboBox extends ComboBox {
  private static final FontInfoRenderer RENDERER = new FontInfoRenderer();

  private Model myModel;

  public FontComboBox() {
    this(false);
  }

  public FontComboBox(boolean withAllStyles) {
    this(withAllStyles, true, false);
  }

  public FontComboBox(boolean withAllStyles, boolean filterNonLatin, boolean noFontItem) {
    super(new Model(withAllStyles, filterNonLatin, noFontItem));
    Dimension size = getPreferredSize();
    size.width = size.height * 8;
    setPreferredSize(size);
    setSwingPopup(true);
    setRenderer(RENDERER);
    getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {}

      @Override
      public void intervalRemoved(ListDataEvent e) {}

      @Override
      public void contentsChanged(ListDataEvent e) {
        ComboPopup popup = FontComboBox.this.getPopup();
        if (popup != null && popup.isVisible()) {
          popup.hide();
          popup.show();
        }
      }
    });
  }

  public boolean isMonospacedOnly() {
    return myModel.myMonospacedOnly;
  }

  public void setMonospacedOnly(boolean monospaced) {
    if (myModel.myMonospacedOnly != monospaced) {
      myModel.myMonospacedOnly = monospaced;
      myModel.updateSelectedItem();
    }
  }

  public String getFontName() {
    Object item = myModel.getSelectedItem();
    return item == null ? null : item.toString();
  }

  public void setFontName(@Nullable String item) {
    myModel.setSelectedItem(item);
  }

  public boolean isNoFontSelected() {
    return myModel.isNoFontSelected();
  }

  @Override
  public void setModel(ComboBoxModel model) {
    if (model instanceof Model) {
      myModel = (Model)model;
      super.setModel(model);
    }
    else {
      throw new UnsupportedOperationException();
    }
  }

  private static final class Model extends AbstractListModel implements ComboBoxModel {
    private final NoFontItem myNoFontItem;
    private volatile List<FontInfo> myAllFonts = Collections.emptyList();
    private volatile List<FontInfo> myMonoFonts = Collections.emptyList();
    private boolean myMonospacedOnly;
    private Object mySelectedItem;

    private Model(boolean withAllStyles, boolean filterNonLatin, boolean noFontItem) {
      myNoFontItem = noFontItem ? new NoFontItem() : null;
      Application application = ApplicationManager.getApplication();
      if (application == null || application.isUnitTestMode()) {
        setFonts(FontInfo.getAll(withAllStyles), filterNonLatin);
      }
      else {
        application.executeOnPooledThread(() -> {
          List<FontInfo> all = FontInfo.getAll(withAllStyles);
          application.invokeLater(() -> {
            setFonts(all, filterNonLatin);
            updateSelectedItem();
          }, application.getAnyModalityState());
        });
      }
    }

    private void setFonts(List<FontInfo> all, boolean filterNonLatin) {
      List<FontInfo> allFonts = new ArrayList<>(all.size());
      List<FontInfo> monoFonts = new ArrayList<>();
      for (FontInfo info : all) {
        if (!filterNonLatin || info.getFont().canDisplayUpTo(info.toString()) == -1) {
          allFonts.add(info);
          if (info.isMonospaced()) {
            monoFonts.add(info);
          }
        }
      }
      myAllFonts = allFonts;
      myMonoFonts = monoFonts;
    }

    private void updateSelectedItem() {
      Object item = getSelectedItem();
      setSelectedItem(null);
      setSelectedItem(item);
    }

    @Override
    public Object getSelectedItem() {
      return mySelectedItem;
    }

    @Override
    public void setSelectedItem(@Nullable Object item) {
      if (item == null && myNoFontItem != null) {
        item = myNoFontItem;
      }
      else {
        if (item instanceof FontInfo) {
          FontInfo info = getInfo(item);
          if (info == null) {
            List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
            item = list.isEmpty() ? null : list.get(0);
          }
        }
        if (item instanceof String) {
          FontInfo info = getInfo(item);
          if (info != null) item = info;
        }
      }
      if (!Comparing.equal(mySelectedItem, item) || item == myNoFontItem) {
        mySelectedItem = item;
        fireContentsChanged(this, -1, -1);
      }
    }

    public boolean isNoFontSelected() {
      return getSelectedItem() == myNoFontItem;
    }

    @Override
    public int getSize() {
      List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
      int size = list.size();
      if (mySelectedItem instanceof String)  size ++;
      if (myNoFontItem != null) size++;
      return size;
    }

    @Override
    public Object getElementAt(int index) {
      int i = index;
      if (myNoFontItem != null) {
        if (index == 0) return myNoFontItem;
        i --;
      }
      List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
      return 0 <= i && i < list.size() ? list.get(i) : mySelectedItem;
    }

    private FontInfo getInfo(Object item) {
      for (FontInfo info : myMonospacedOnly ? myMonoFonts : myAllFonts) {
        if (item instanceof String ? info.toString().equalsIgnoreCase((String)item) : info.equals(item)) {
          return info;
        }
      }
      return null;
    }

    private final static class NoFontItem {
      @Override
      public String toString() {
        return ApplicationBundle.message("settings.editor.font.none");
      }
    }
  }
}
