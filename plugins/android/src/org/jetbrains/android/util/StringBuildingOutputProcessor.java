package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene.Kudelevsky
 */
public class StringBuildingOutputProcessor implements OutputProcessor {
  private final StringBuffer myBuffer = new StringBuffer();

  @Override
  public void onTextAvailable(@NotNull String text) {
    myBuffer.append(text);
  }

  @NotNull
  public String getMessage() {
    return myBuffer.toString();
  }
}
