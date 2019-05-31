// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.mac.touchbar;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.ui.mac.foundation.ID;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.Objects;

class TBItemButton extends TBItem {
  protected final @Nullable TouchBarStats.AnActionStats myActionStats;

  protected @Nullable Icon myOriginIcon;
  protected @Nullable Icon myIcon;
  protected @Nullable String myText;
  protected int myLayoutBits = 0;
  protected int myFlags = 0;
  protected boolean myHasArrowIcon = false;
  protected int myUpdateOptions;

  private @Nullable Runnable myAction;
  private @Nullable NSTLibrary.Action myNativeCallback;

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

  TBItemButton setIcon(Icon icon) {
    if (!Objects.equals(icon, myOriginIcon)) {
      myOriginIcon = icon;
      myIcon = getDarkIcon(myOriginIcon);
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
    }

    return this;
  }

  TBItemButton setHasArrowIcon(boolean hasArrowIcon) {
    if (hasArrowIcon != myHasArrowIcon) {
      myHasArrowIcon = hasArrowIcon;
      if (myNativePeer != ID.NIL) {
        final Icon ic = myHasArrowIcon ? IconLoader.getIcon("/mac/touchbar/popoverArrow_dark.svg") : null;
        NST.setArrowImage(myNativePeer, ic);
      }
    }
    return this;
  }

  TBItemButton setText(String text) {
    if (!Comparing.equal(text, myText)) {
      myText = text;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
    }

    return this;
  }

  TBItemButton setAction(Runnable action, boolean executeOnEDT, ModalityState modality) {
    if (action != myAction) {
      myAction = action;
      if (myAction == null)
        myNativeCallback = null;
      else
        myNativeCallback = ()->{
          // NOTE: executed from AppKit thread
          if (executeOnEDT) {
            final Application app = ApplicationManager.getApplication();
            if (app != null) {
              if (modality != null)
                app.invokeLater(myAction, modality);
              else
                app.invokeLater(myAction);
            } else
              SwingUtilities.invokeLater(myAction);
          } else {
            myAction.run();
          }

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

  TBItemButton setToggle(boolean toggle) {
    int flags = _applyFlag(myFlags, toggle, NSTLibrary.BUTTON_FLAG_TOGGLE);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }

    return this;
  }

  TBItemButton setColored(boolean isColored) {
    final int flags = _applyFlag(myFlags, isColored, NSTLibrary.BUTTON_FLAG_COLORED);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }

    return this;
  }

  TBItemButton setSelected(boolean isSelected) {
    final int flags = _applyFlag(myFlags, isSelected, NSTLibrary.BUTTON_FLAG_SELECTED);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }

    return this;
  }

  TBItemButton setDisabled(boolean isDisabled) {
    final int flags = _applyFlag(myFlags, isDisabled, NSTLibrary.BUTTON_FLAG_DISABLED);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }

    return this;
  }

  TBItemButton setTransparentBg(boolean isTransparentBg) {
    final int flags = _applyFlag(myFlags, isTransparentBg, NSTLibrary.BUTTON_FLAG_TRANSPARENT_BG);
    if (flags != myFlags) {
      myFlags = flags;
      myUpdateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }

    return this;
  }

  void updateNativePeerIfNecessary() {
    if (myUpdateOptions != 0 && myNativePeer != ID.NIL)
      _updateNativePeer();
  }

  @Override
  protected void _updateNativePeer() {
    final long startNs = myActionStats != null ? System.nanoTime() : 0;
    final Icon icon = (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_IMG) != 0 ? myIcon : null;
    final String text = (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_TEXT) != 0 ? myText : null;
    final NSTLibrary.Action callback = (myUpdateOptions & NSTLibrary.BUTTON_UPDATE_ACTION) != 0 ? myNativeCallback : null;
    final int validFlags = _validateFlags();
    NST.updateButton(myNativePeer, myUpdateOptions, myLayoutBits, validFlags, text, icon, callback, myActionStats);
    myUpdateOptions = 0;
    if (myActionStats != null) {
      myActionStats.iconUpdateNativePeerCount++;
      myActionStats.iconUpdateNativePeerDurationNs += System.nanoTime() - startNs;
    }
  }

  @Override
  protected ID _createNativePeer() {
    final ID result = NST.createButton(getUid(), myLayoutBits, _validateFlags(), myText, myIcon, myNativeCallback);
    if (myHasArrowIcon) {
      final Icon ic = IconLoader.getIcon("/mac/touchbar/popoverArrow_dark.svg");
      NST.setArrowImage(result, ic);
    }
    return result;
  }

  private int _validateFlags() {
    if ((myFlags & NSTLibrary.BUTTON_FLAG_COLORED) != 0 && (myFlags & NSTLibrary.BUTTON_FLAG_DISABLED) != 0)
      return myFlags & ~NSTLibrary.BUTTON_FLAG_COLORED;
    return myFlags;
  }

  private static int _applyFlag(int src, boolean include, int flag) {
    return include ? (src | flag) : (src & ~flag);
  }
}
