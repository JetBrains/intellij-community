package com.intellij.cvsSupport2;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;

/**
 * author: lesya
 */
public abstract class Progress {
  public abstract void setText(String text);

  public static final Progress DEAF = new Progress() {
    public void setText(String text) {

    }
  };

  public static Progress create(){
    final ProgressIndicator progressIndicator = ProgressManager.getInstance().getProgressIndicator();
    if (progressIndicator != null) {
      return new Progress() {
        public void setText(String text) {
          progressIndicator.setText2(text);
        }
      };
    }
    else {
      return new Progress() {
        public void setText(String text) {
        }
      };
    }

  }
}
