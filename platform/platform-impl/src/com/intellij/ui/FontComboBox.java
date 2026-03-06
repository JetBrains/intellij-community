// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationBundle;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.FontInfo;
import com.intellij.util.ui.JBDimension;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.Unmodifiable;

import javax.swing.AbstractListModel;
import javax.swing.ComboBoxModel;
import javax.swing.event.ListDataEvent;
import javax.swing.event.ListDataListener;
import javax.swing.plaf.basic.ComboPopup;
import java.awt.Dimension;
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
    setupDefaultRenderer(false, true);
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

  /**
   * Customizes the renderer for the font combo box.
   *
   * @param isEditorFont whether the fonts are used in the editor or in the UI
   * @param showGroups whether to show Monospaced/Proportional font groups. Should be false if the combobox uses
   *                   monospaced fonts only.
   */
  public void setupDefaultRenderer(boolean isEditorFont, boolean showGroups) {
    var builder = new FontInfoRendererBuilder();

    if (isEditorFont) {
      builder.editorFont();
    }
    if (showGroups) {
      builder.withSeparatorFontType(() -> myModel.myAllFonts, () -> myModel.myMonoFonts);
    }

    //noinspection unchecked
    setRenderer(builder.buildFontComboBoxRenderer());
  }

  static final class Model extends AbstractListModel implements ComboBoxModel {
    private final NoFontItem myNoFontItem;
    private LoadingFontsItem myLoadingFontsItem = null;

    private volatile @NotNull List<FontInfo> myAllFonts = Collections.emptyList();
    private volatile @NotNull List<FontInfo> myMonoFonts = Collections.emptyList();
    private boolean myMonospacedOnly;
    private Object mySelectedItem;

    private Model(boolean withAllStyles, boolean filterNonLatin, boolean noFontItem) {
      myNoFontItem = noFontItem ? new NoFontItem() : null;
      Application application = ApplicationManager.getApplication();
      if (application == null || application.isUnitTestMode()) {
        setFonts(FontInfo.getAll(withAllStyles), filterNonLatin);
      }
      else {
        myLoadingFontsItem = new LoadingFontsItem();
        application.executeOnPooledThread(() -> {
          List<FontInfo> all = FontInfo.getAll(withAllStyles);
          application.invokeLater(() -> {
            myLoadingFontsItem = null;
            setFonts(all, filterNonLatin);
            onModelToggled();
          }, ModalityState.any());
        });
      }
    }

    private void setFonts(@NotNull List<FontInfo> all, boolean filterNonLatin) {
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
            @NotNull List<FontInfo> list = myMonospacedOnly ? myMonoFonts : myAllFonts;
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
      return getAllElements().size();
    }

    @Override
    public Object getElementAt(int index) {
      List<Object> items = getAllElements();
      return 0 <= index && index < items.size() ? items.get(index) : null;
    }

    private @NotNull @Unmodifiable List<Object> getAllElements() {
      List<List<?>> groups = new ArrayList<>();
      if (myNoFontItem != null) {
        groups.add(Collections.singletonList(myNoFontItem));
      }
      groups.add(myMonospacedOnly ? myMonoFonts : myAllFonts);
      if (mySelectedItem instanceof String) {
        groups.add(Collections.singletonList(mySelectedItem));
      }
      if (myLoadingFontsItem != null) {
        groups.add(Collections.singletonList(myLoadingFontsItem));
      }
      return ContainerUtil.concat(groups);
    }

    private FontInfo getInfo(Object item) {
      for (FontInfo info : myMonospacedOnly ? myMonoFonts : myAllFonts) {
        if (item instanceof String ? info.toString().equalsIgnoreCase((String)item) : info.equals(item)) {
          return info;
        }
      }
      return null;
    }

    static final class NoFontItem {
      @Override
      public @NlsSafe String toString() {
        return ApplicationBundle.message("settings.editor.font.none");
      }
    }

    static final class LoadingFontsItem {
      @Override
      public @NlsSafe String toString() {
        return ApplicationBundle.message("settings.editor.font.loading");
      }
    }
  }
}
