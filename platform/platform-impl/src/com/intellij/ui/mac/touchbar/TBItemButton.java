// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.mac.touchbar;

import com.intellij.icons.AllIcons;
import com.intellij.ide.ActivityTracker;
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressIndicatorProvider;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.Pair;
import com.intellij.ui.mac.foundation.ID;
import com.intellij.util.TimeoutUtil;
import com.intellij.util.concurrency.AppExecutorUtil;
import com.sun.jna.Pointer;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;
import org.jetbrains.concurrency.AsyncPromise;

import javax.swing.*;
import java.awt.*;
import java.util.Objects;
import java.util.concurrent.Executor;

@ApiStatus.Internal
public class TBItemButton extends TBItem {
  private static final int TEST_DELAY_MS = Integer.getInteger("touchbar.test.delay", 0);
  private static final Executor ourExecutor = AppExecutorUtil.createBoundedApplicationPoolExecutor("Touchbar buttons updater", 2);

  final @Nullable TouchBarStats.AnActionStats actionStats;

  private @Nullable String myText;
  private @Nullable String myHint;
  private boolean myIsHintDisabled = false;
  private int myLayoutBits = 0;
  private boolean myHasArrowIcon = false;
  private boolean isGetDisabledIconNeeded = false;
  private AsyncPromise<Pair<Pointer, Dimension>> rasterPromise;

  // action parameters
  private @Nullable Runnable action;
  private @Nullable NSTLibrary.Action nativeCallback;
  private boolean executeOnEdt = true;
  private ModalityState modality = null;

  protected @Nullable Icon originIcon;
  protected int myFlags = 0;
  protected int updateOptions;

  TBItemButton(@Nullable ItemListener listener, @Nullable TouchBarStats.AnActionStats actionStats) {
    super("button", listener);
    this.actionStats = actionStats;
  }

  private @Nullable Icon getDarkIcon(@Nullable Icon icon) {
    if (icon == null) {
      return null;
    }

    long startNs = actionStats == null ? 0 : System.nanoTime();
    icon = IconLoader.getDarkIcon(icon, true);
    if (actionStats != null) {
      actionStats.iconGetDarkDurationNs += System.nanoTime() - startNs;
    }

    return icon;
  }

  void setIcon(Icon icon, boolean needGetDisabled) {
    if (isGetDisabledIconNeeded != needGetDisabled || !Objects.equals(icon, originIcon)) {
      originIcon = icon;
      isGetDisabledIconNeeded = needGetDisabled;
      updateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
    }
  }

  @VisibleForTesting
  public TBItemButton setIcon(Icon icon) {
    if (!Objects.equals(icon, originIcon) || isGetDisabledIconNeeded) {
      originIcon = icon;
      isGetDisabledIconNeeded = false;
      updateOptions |= NSTLibrary.BUTTON_UPDATE_IMG;
    }

    return this;
  }

  // convenience method
  void setIconFromPresentation(@NotNull Presentation presentation) {
    Icon icon;
    boolean needGetDisabledIcon = false;
    if (presentation.isEnabled()) {
      icon = presentation.getIcon();
    }
    else {
      icon = presentation.getDisabledIcon();
      if (icon == null && presentation.getIcon() != null) {
        needGetDisabledIcon = true;
        icon = presentation.getIcon();
      }
    }

    setIcon(icon, needGetDisabledIcon);
  }

  // convenience method
  void setIconAndTextFromPresentation(@NotNull Presentation presentation, @Nullable TouchbarActionCustomizations touchBarAction) {
    if (touchBarAction != null) {
      if (touchBarAction.isShowImage() && touchBarAction.isShowText()) {
        setIconFromPresentation(presentation);
        setText(presentation.getText());
      }
      else if (touchBarAction.isShowText()) {
        setText(presentation.getText());
        setIcon(null);
      }
      else {
        setIconFromPresentation(presentation);
        setText(originIcon != null ? null : presentation.getText());
      }
    }
    else {
      setIconFromPresentation(presentation);
      setText(originIcon != null ? null : presentation.getText());
    }
  }

  void setHasArrowIcon(boolean hasArrowIcon) {
    if (hasArrowIcon != myHasArrowIcon) {
      myHasArrowIcon = hasArrowIcon;
      synchronized (this) {
        if (myNativePeer != ID.NIL) {
          NST.setArrowImage(myNativePeer, myHasArrowIcon ? AllIcons.Mac.Touchbar.PopoverArrow : null);
        }
      }
    }
  }

  @VisibleForTesting
  public TBItemButton setText(String text) {
    if (!Objects.equals(text, myText)) {
      myText = text;
      updateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
    }

    return this;
  }

  void setText(String text, String hint, boolean isHintDisabled) {
    if (!Objects.equals(text, myText) || !Objects.equals(hint, myHint) || (hint != null && myIsHintDisabled != isHintDisabled)) {
      myText = text;
      myHint = hint;
      myIsHintDisabled = isHintDisabled;
      updateOptions |= NSTLibrary.BUTTON_UPDATE_TEXT;
    }
  }

  void setModality(ModalityState modality) {
    this.modality = modality;
  }

  @VisibleForTesting
  public TBItemButton setAction(Runnable action, boolean executeOnEDT) {
    if (action == this.action && this.executeOnEdt == executeOnEDT) {
      return this;
    }

    this.action = action;
    this.executeOnEdt = executeOnEDT;
    if (action == null) {
      nativeCallback = null;
    }
    else {
      nativeCallback = () -> {
        // NOTE: executed from AppKit thread
        if (executeOnEdt) {
          Application app = ApplicationManager.getApplication();
          if (modality == null) {
            app.invokeLater(this.action);
          }
          else {
            app.invokeLater(this.action, modality);
          }
        }
        else {
          this.action.run();
        }

        ActivityTracker.getInstance().inc();

        if (myListener != null) {
          myListener.onItemEvent(this, 0);
        }
      };
    }

    updateOptions |= NSTLibrary.BUTTON_UPDATE_ACTION;

    return this;
  }

  @VisibleForTesting
  public TBItemButton setWidth(int width) { return setLayout(width, 0, 2, 8); }

  TBItemButton setLayout(int width, int widthFlags, int margin, int border) {
    if (width < 0) {
      width = 0;
    }
    if (margin < 0) {
      margin = 0;
    }
    if (border < 0) {
      border = 0;
    }

    int newLayout = width & NSTLibrary.LAYOUT_WIDTH_MASK;
    newLayout |= widthFlags;
    newLayout |= NSTLibrary.margin2mask((byte)margin);
    newLayout |= NSTLibrary.border2mask((byte)border);
    if (myLayoutBits != newLayout) {
      myLayoutBits = newLayout;
      updateOptions |= NSTLibrary.BUTTON_UPDATE_LAYOUT;
    }

    return this;
  }

  @VisibleForTesting
  public void setToggle() { _setFlag(NSTLibrary.BUTTON_FLAG_TOGGLE, true); }

  void setColored() { _setFlag(NSTLibrary.BUTTON_FLAG_COLORED, true); }

  void setSelected(boolean isSelected) {
    _setFlag(NSTLibrary.BUTTON_FLAG_SELECTED, isSelected);
  }

  void setDisabled(boolean isDisabled) {
    _setFlag(NSTLibrary.BUTTON_FLAG_DISABLED, isDisabled);
  }

  TBItemButton setTransparentBg() { return _setFlag(NSTLibrary.BUTTON_FLAG_TRANSPARENT_BG, true); }

  private TBItemButton _setFlag(int nstLibFlag, boolean val) {
    final int flags = _applyFlag(myFlags, val, nstLibFlag);
    if (flags != myFlags) {
      myFlags = flags;
      updateOptions |= NSTLibrary.BUTTON_UPDATE_FLAGS;
    }
    return this;
  }

  // Icon calculations can be slow, so we use async update
  void updateLater(boolean force) {
    if (!force && (!myIsVisible || updateOptions == 0 || myNativePeer == ID.NIL)) {
      return;
    }

    if (rasterPromise != null && !rasterPromise.isDone()) {
      rasterPromise.cancel();
    }

    rasterPromise = new AsyncPromise<>();
    rasterPromise.onSuccess(raster -> {
      //
      // update native peer
      //
      int updateOptions = this.updateOptions;
      String text = (updateOptions & NSTLibrary.BUTTON_UPDATE_TEXT) != 0 ? myText : null;
      String hint = (updateOptions & NSTLibrary.BUTTON_UPDATE_TEXT) != 0 ? myHint : null;
      NSTLibrary.Action callback = (updateOptions & NSTLibrary.BUTTON_UPDATE_ACTION) != 0 ? nativeCallback : null;
      int validFlags = _validateFlags();
      int isHintDisabled = myIsHintDisabled ? 1 : 0;
      int layoutBits = myLayoutBits;

      this.updateOptions = 0;

      synchronized (this) {
        if (myNativePeer.equals(ID.NIL)) {
          return;
        }
        NST.updateButton(myNativePeer, updateOptions, layoutBits, validFlags, text, hint, isHintDisabled, raster, callback);
      }
    });

    ourExecutor.execute(() -> {
      if (originIcon == null || (!force && (updateOptions & NSTLibrary.BUTTON_UPDATE_IMG) == 0)) {
        if (TEST_DELAY_MS > 0) waitTheTestDelay();
        rasterPromise.setResult(null);
        return;
      }

      // load icon (can be quite slow)
      long startNs = actionStats == null ? 0 : System.nanoTime();
      if (TEST_DELAY_MS > 0) {
        waitTheTestDelay();
      }

      Icon icon = getDarkIcon(originIcon);
      if (icon != null && isGetDisabledIconNeeded) {
        icon = IconLoader.getDisabledIcon(icon);
      }

      // prepare raster (not very fast)
      Pair<Pointer, Dimension> raster = NST.get4ByteRGBARaster(icon);
      if (actionStats != null && raster != null) {
        actionStats.iconUpdateIconRasterCount++;
        actionStats.iconRenderingDurationNs += System.nanoTime() - startNs;
      }

      rasterPromise.setResult(raster);
    });
  }

  private static void waitTheTestDelay() {
    if (TEST_DELAY_MS <= 0) {
      return;
    }

    ProgressIndicator progress = Objects.requireNonNull(ProgressIndicatorProvider.getGlobalProgressIndicator());
    long start = System.currentTimeMillis();
    while (true) {
      progress.checkCanceled();
      if (System.currentTimeMillis() - start > TEST_DELAY_MS) {
        break;
      }
      TimeoutUtil.sleep(1);
    }
  }

  @Override
  protected ID _createNativePeer() {
    if (originIcon != null && rasterPromise == null) {
      updateLater(true);
    }
    final ID result = NST.createButton(
      getUid(),
      myLayoutBits, _validateFlags(),
      myText, myHint, myIsHintDisabled ? 1 : 0,
      rasterPromise == null || !rasterPromise.isSucceeded() ? null : rasterPromise.get(),
      nativeCallback);
    if (myHasArrowIcon) {
      NST.setArrowImage(result, AllIcons.Mac.Touchbar.PopoverArrow);
    }

    return result;
  }

  private int _validateFlags() {
    int flags = myFlags;
    if ((flags & NSTLibrary.BUTTON_FLAG_COLORED) != 0 && (flags & NSTLibrary.BUTTON_FLAG_DISABLED) != 0) {
      return flags & ~NSTLibrary.BUTTON_FLAG_COLORED;
    }
    return flags;
  }

  private static int _applyFlag(int src, boolean include, int flag) {
    return include ? (src | flag) : (src & ~flag);
  }
}
