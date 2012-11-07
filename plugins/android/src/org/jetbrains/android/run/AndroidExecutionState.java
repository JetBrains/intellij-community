package org.jetbrains.android.run;

import com.android.ddmlib.IDevice;
import com.intellij.execution.ui.ConsoleView;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * @author Eugene.Kudelevsky
 */
public interface AndroidExecutionState {
  @Nullable
  IDevice[] getDevices();

  @Nullable
  ConsoleView getConsoleView();

  @NotNull
  AndroidRunConfigurationBase getConfiguration();
}
