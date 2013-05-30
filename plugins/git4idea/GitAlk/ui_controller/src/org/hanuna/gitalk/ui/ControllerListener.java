package org.hanuna.gitalk.ui;

import org.hanuna.gitalk.commit.Hash;
import org.jetbrains.annotations.NotNull;

/**
 * @author erokhins
 */
public interface ControllerListener {

  public void jumpToRow(int rowIndex);

  public void updateUI();

  public void setState(@NotNull State state);

  public void setErrorMessage(@NotNull String errorMessage);

  public void setUpdateProgressMessage(@NotNull String progressMessage);

  void addToSelection(Hash hash);

  //void addToSelection(int rowIndex);

  public static enum State {
    USUAL,
    ERROR,
    PROGRESS
  }
}
