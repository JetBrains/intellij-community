package com.intellij.openapi.progress.util;

import com.intellij.openapi.Disposable;

public interface ProgressDialog extends Disposable {
  void prepareShowDialog(int delay);

  @Override
  void dispose();

  void setShouldShowBackground(boolean shouldShowBackground);

  void changeCancelButtonText(String text);

  void cancel();

  void enableCancelButtonIfNeeded(boolean enable);

  void update();

  void setWillBeSheduledForRestore();

  void background();

  void startBlocking(boolean shouldShowCancel);

  boolean isShowing();

  void hide();

  void show();

  boolean isPopupWasShown();
}
