package org.jetbrains.android.util;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidBuildTestingManager {

  private static AndroidBuildTestingManager ourTestingManager;

  private final MyCommandExecutor myCommandExecutor;

  private AndroidBuildTestingManager(@NotNull MyCommandExecutor executor) {
    myCommandExecutor = executor;
  }

  @Nullable
  public static AndroidBuildTestingManager getTestingManager() {
    return ourTestingManager;
  }

  public static void startBuildTesting(@NotNull MyCommandExecutor commandExecutor) {
    ourTestingManager = new AndroidBuildTestingManager(commandExecutor);
  }

  @NotNull
  public static String arrayToString(@NotNull String[] array) {
    final StringBuilder builder = new StringBuilder("[");

    for (String s : array) {
      builder.append('\n').append(s);
    }
    if (array.length > 0) {
      builder.append('\n');
    }
    builder.append("]");
    return builder.toString();
  }

  @NotNull
  public MyCommandExecutor getCommandExecutor() {
    return myCommandExecutor;
  }

  public interface MyCommandExecutor {
    @NotNull
    Process createProcess(@NotNull String[] args, @NotNull Map<? extends String, ? extends String> environment);

    void log(@NotNull String s);

    void checkJarContent(@NotNull String jarId, @NotNull String jarPath);
  }
}
