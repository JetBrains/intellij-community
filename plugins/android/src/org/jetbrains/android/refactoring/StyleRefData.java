package org.jetbrains.android.refactoring;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
* @author Eugene.Kudelevsky
*/
class StyleRefData {
  private final String myStyleName;
  private final String myStylePackage;

  StyleRefData(@NotNull String styleName, @Nullable String stylePackage) {
    myStyleName = styleName;
    myStylePackage = stylePackage;
  }

  @NotNull
  public String getStyleName() {
    return myStyleName;
  }

  @Nullable
  public String getStylePackage() {
    return myStylePackage;
  }
}
