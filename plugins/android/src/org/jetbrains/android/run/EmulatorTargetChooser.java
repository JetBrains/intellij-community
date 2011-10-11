package org.jetbrains.android.run;

import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public class EmulatorTargetChooser implements TargetChooser {
  private final String myAvd;

  public EmulatorTargetChooser(@Nullable String avd) {
    assert avd == null || avd.length() > 0;
    myAvd = avd;
  }

  @Nullable
  public String getAvd() {
    return myAvd;
  }
}
