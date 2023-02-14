// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.ui.popup.ListSeparator;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FontInfo;
import com.intellij.util.ui.JBDimension;
import com.intellij.util.ui.JBUI;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class FontComboBox extends AbstractFontCombo {

  private Model myModel;
  private final JBDimension mySize;

  public FontComboBox() {
    this(false);
  }

  public FontComboBox(boolean withAllStyles) {
    this(withAllStyles, true, false);
  }

  public FontComboBox(boolean withAllStyles, boolean filterNonLatin, boolean noFontItem) {
    super(new Model(withAllStyles, filterNonLatin, noFontItem));
    Dimension size = super.getPreferredSize();
    size.width = size.height * 8;
    // preScaled=true as 'size' reflects already scaled font
    mySize = JBDimension.create(size, true);
    setSwingPopup(false);
    //noinspection unchecked
    setRenderer(new GroupedComboBoxRenderer(this) {
      @Nullable
      @Override
      public ListSeparator separatorFor(Object value) {
        if (getModel() instanceof Model m && value instanceof FontInfo info) {
          if (!m.myMonoFonts.isEmpty() && m.myMonoFonts.get(0) == info)
            return new ListSeparator(ApplicationBundle.message("settings.editor.font.monospaced"));
          if (!m.myAllFonts.isEmpty() && ContainerUtil.find(m.myAllFonts, f -> !f.isMonospaced()) == info)
            return new ListSeparator(ApplicationBundle.message("settings.editor.font.proportional"));
        }
        return null;
      }

      @Override
      public void customize(@NotNull SimpleColoredComponent item, Object value, int index) {
        if (value instanceof FontInfo info) {
          item.setFont(index == -1 ? JBUI.Fonts.label() : info.getFont());
          item.append(info.toString());
        }
        else if (value instanceof Model.NoFontItem nfi) {
          item.append(nfi.toString());
        }
      }
    });
    getModel().addListDataListener(new ListDataListener() {
      @Override
      public void intervalAdded(ListDataEvent e) {}

      @Override
      public void intervalRemoved(ListDataEvent e) {}

      @Override
      public void contentsChanged(ListDataEvent e) {
        if (e.getIndex0() != -42 || e.getIndex1() != -42) return;
        ComboPopup popup = FontComboBox.this.getPopup();
        if (popup != null && popup.isVisible()) {
          popup.hide();
          popup.show();
        }
      }
    });
  }

  @Override
  public Dimension getPreferredSize() {
    if (isPreferredSizeSet()) return super.getPreferredSize();
    return mySize.size();
  }

  @Override
  public boolean isMonospacedOnly() {
    return myModel.myMonospacedOnly;
  }

  @Override
  public boolean isMonospacedOnlySupported() {
    return true;
  }

  @Override
  public void setMonospacedOnly(boolean monospaced) {
    myModel.setMonospacedOnly(monospaced);
  }

  @Override
  public String getFontName() {
    Object item = myModel.getSelectedItem();
    return item == null ? null : item.toString();
  }

  @Override
  public void setFontName(@NlsSafe @Nullable String item) {
    myModel.setSelectedItem(item);
  }

  @Override
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
            onModelToggled();
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

    public void setMonospacedOnly(boolean monospaced) {
      if (myMonospacedOnly != monospaced) {
        myMonospacedOnly = monospaced;
        onModelToggled();
      }
    }

    void onModelToggled() {
      Object item = getSelectedItem();
      setSelectedItem(null);
      setSelectedItem(item);
      fireContentsChanged(this, -42, -42);
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
      public @NlsSafe String toString() {
        return ApplicationBundle.message("settings.editor.font.none");
      }
    }
  }
}
