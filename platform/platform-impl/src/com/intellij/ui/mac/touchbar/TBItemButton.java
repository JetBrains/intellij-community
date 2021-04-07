// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.mac.TouchbarDataKeys;
import com.intellij.ui.mac.foundation.ID;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;

class TBItemButton extends TBItem {
  protected final @Nullable TouchBarStats.AnActionStats myActionStats;

  private @Nullable String myText;
  private @Nullable String myHint;
  private boolean myIsHintDisabled = false;
  private int myLayoutBits = 0;
  private boolean myHasArrowIcon = false;

  private boolean myNeedGetDisabledIcon = false;

  // action parameters
  private @Nullable Runnable myAction;
  private @Nullable NSTLibrary.Action myNativeCallback;
  private boolean myExecuteOnEDT = true;
  private ModalityState myModality = null;

  protected @Nullable Icon myOriginIcon;
  protected  int myFlags = 0;
  protected int myUpdateOptions;

  TBItemButton(@Nullable ItemListener listener, @Nullable TouchBarStats.AnActionStats actionStats) {
    super("button", listener);
    myActionStats = actionStats;
  }

  private @Nullable Icon getDarkIcon(@Nullable Icon icon) {
    if (icon == null)
      return null;

    final long startNs = myActionStats != null ? System.nanoTime() : 0;
    icon = IconLoader.getDarkIcon(icon, true);
    if (myActionStats != null)
      myActionStats.iconGetDarkDurationNs += System.nanoTime() - startNs;

    return icon;
  }

  TBItemButton setIcon(Icon icon, boolean needGetDisabled) {
    if (!Objects.equals(icon, myOriginIcon) || myNeedGetDisabledIcon != needGetDisabled) {
      myOriginIcon = icon;
      myNeedGetDisabledIcon = needGetDisabled;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
    }

    return this;
  }

  TBItemButton setIcon(Icon icon) {
    if (!Objects.equals(icon, myOriginIcon) || myNeedGetDisabledIcon) {
      myOriginIcon = icon;
      myNeedGetDisabledIcon = false;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
    }

    return this;
  }

  // convenience method
  TBItemButton setIconFromPresentation(@NotNull Presentation presentation) {
    Icon icon;
    boolean needGetDisabledIcon = false;
    if (presentation.isEnabled())
      icon = presentation.getIcon();
    else {
      icon = presentation.getDisabledIcon();
      if (icon == null && presentation.getIcon() != null) {
        needGetDisabledIcon = true;
        icon = presentation.getIcon();
      }
    }

    return setIcon(icon, needGetDisabledIcon);
  }

  // convenience method
  TBItemButton setIconAndTextFromPresentation(@NotNull Presentation presentation, @Nullable TouchbarDataKeys.ActionDesc pd) {
    if (pd != null) {
      if (pd.isShowImage() && pd.isShowText()) {
        setIconFromPresentation(presentation);
        setText(presentation.getText());
      } else if (pd.isShowText()) {
        setText(presentation.getText());
        setIcon(null);
      } else {
        setIconFromPresentation(presentation);
        setText(myOriginIcon != null ? null : presentation.getText());
      }
    } else {
      setIconFromPresentation(presentation);
      setText(myOriginIcon != null ? null : presentation.getText());
    }
    return this;
  }

  TBItemButton setHasArrowIcon(boolean hasArrowIcon) {
    if (hasArrowIcon != myHasArrowIcon) {
      myHasArrowIcon = hasArrowIcon;
      if (getNativePeer() != ID.NIL) {
        final Icon ic = myHasArrowIcon ? AllIcons.Mac.Touchbar.PopoverArrow : null;
        NST.setArrowImage(getNativePeer(), ic);
      }
    }
    return this;
  }

  TBItemButton setText(String text) {
    if (!Objects.equals(text, myText)) {
      myText = text;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
    }

    return this;
  }

  TBItemButton setText(String text, String hint, boolean isHintDisabled) {
    if (!Objects.equals(text, myText) || !Objects.equals(hint, myHint) || (hint != null && myIsHintDisabled != isHintDisabled)) {
      myText = text;
      myHint = hint;
      myIsHintDisabled = isHintDisabled;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
    }

    return this;
  }

  TBItemButton setModality(ModalityState modality) { myModality = modality; return this; }

  TBItemButton setAction(Runnable action, boolean executeOnEDT) {
    if (action != myAction || myExecuteOnEDT != executeOnEDT) {
      myAction = action;
      myExecuteOnEDT = executeOnEDT;
      if (myAction == null)
        myNativeCallback = null;
      else
        myNativeCallback = ()->{
          // NOTE: executed from AppKit thread
          if (myExecuteOnEDT) {
            final @NotNull Application app = ApplicationManager.getApplication();
            if (myModality != null)
              app.invokeLater(myAction, myModality);
            else
              app.invokeLater(myAction);
          } else {
            myAction.run();
          }

          ActivityTracker.getInstance().inc();

          if (myListener != null)
            myListener.onItemEvent(this, 0);
        };

      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_ACTION;
    }

    return this;
  }
  TBItemButton setWidth(int width) { return setLayout(width, 0, 2, 8); }

  TBItemButton setLayout(int width, int widthFlags, int margin, int border) {
    if (width < 0)
      width = 0;
    if (margin < 0)
      margin = 0;
    if (border < 0)
      border = 0;

    int newLayout = width & NSTLibrary.LAYOUT_WIDTH_MASK;
    newLayout |= widthFlags;
    newLayout |= NSTLibrary.margin2mask((byte)margin);
    newLayout |= NSTLibrary.border2mask((byte)border);
    if (myLayoutBits != newLayout) {
      myLayoutBits = newLayout;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_LAYOUT;
    }

    return this;
  }

  // [-128, 127], 0 is the default value
  TBItemButton setPriority(byte prio) {
    final int flags = myFlags | NSTLibrary.priority2mask(prio);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }

    return this;
  }

  TBItemButton setToggle() { return _setFlag(NSTLibrary.BUTTON_FLAG_TOGGLE, true); }

  TBItemButton setColored() { return _setFlag(NSTLibrary.BUTTON_FLAG_COLORED, true); }

  TBItemButton setSelected(boolean isSelected) {
    return _setFlag(NSTLibrary.BUTTON_FLAG_SELECTED, isSelected);
  }

  TBItemButton setDisabled(boolean isDisabled) {
    return _setFlag(NSTLibrary.BUTTON_FLAG_DISABLED, isDisabled);
  }

  TBItemButton setTransparentBg() { return _setFlag(NSTLibrary.BUTTON_FLAG_TRANSPARENT_BG, true); }

  private TBItemButton _setFlag(int nstLibFlag, boolean val) {
    final int flags = _applyFlag(myFlags, val, nstLibFlag);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }
    return this;
  }

  class Updater {
    private Pair<Pointer, Dimension> myRaster = null;

    void prepareUpdateData() {
      if (myOriginIcon == null || (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_IMG) == 0)
        return;

      final long startNs = myActionStats != null ? System.nanoTime() : 0;
      final Icon darkIcon = getDarkIcon(myOriginIcon);
      if (darkIcon == null)
        return;

      final Icon icon = myNeedGetDisabledIcon ? IconLoader.getDisabledIcon(darkIcon) : darkIcon;

      myRaster = NST.get4ByteRGBARaster(icon);
      if (myActionStats != null && myRaster != null) {
        myActionStats.iconUpdateIconRasterCount++;
        myActionStats.iconRenderingDurationNs += System.nanoTime() - startNs;
      }
    }
    void updateNativePeer() {
      final int updateOptions = myUpdateOptions;
      final String text = (updateOptions & NSTLibrary.BUTTON_UPDATE_TEXT) != 0 ? myText : null;
      final String hint = (updateOptions & NSTLibrary.BUTTON_UPDATE_TEXT) != 0 ? myHint : null;
      final NSTLibrary.Action callback = (updateOptions & NSTLibrary.BUTTON_UPDATE_ACTION) != 0 ? myNativeCallback : null;
      final int validFlags = _validateFlags();
      final int isHintDisabled = myIsHintDisabled ? 1 : 0;
      final int layoutBits = myLayoutBits;
      final Pair<Pointer, Dimension> raster = myRaster;

      myUpdateOptions = 0;

      synchronized (TBItemButton.this) {
        final ID nativePeer = getNativePeer();
        if (nativePeer.equals(ID.NIL))
          return;
        NST.updateButton(nativePeer, updateOptions, layoutBits, validFlags, text, hint, isHintDisabled, raster, callback);
      }
    }
  }

  @Nullable Updater getNativePeerUpdater() {
    if (!myIsVisible || myUpdateOptions == 0 || getNativePeer() == ID.NIL)
      return null;

    return new Updater();
  }

  @Override
  protected ID _createNativePeer() {
    Icon icon = null;
    if (myOriginIcon != null) {
      icon = ReadAction.compute(() -> getDarkIcon(myOriginIcon));
    }
    final ID result = NST.createButton(getUid(), myLayoutBits, _validateFlags(), myText, myHint, myIsHintDisabled ? 1 : 0, icon, myNativeCallback);
    if (myHasArrowIcon) {
      final Icon ic = AllIcons.Mac.Touchbar.PopoverArrow;
      NST.setArrowImage(result, ic);
    }

    return result;
  }

  private int _validateFlags() {
    int flags = myFlags;
    if ((flags & NSTLibrary.BUTTON_FLAG_COLORED) != 0 && (flags & NSTLibrary.BUTTON_FLAG_DISABLED) != 0)
      return flags & ~NSTLibrary.BUTTON_FLAG_COLORED;
    return flags;
  }

  private static int _applyFlag(int src, boolean include, int flag) {
    return include ? (src | flag) : (src & ~flag);
  }
}
