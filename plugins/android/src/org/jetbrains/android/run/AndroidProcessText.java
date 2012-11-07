package org.jetbrains.android.run;

import com.intellij.execution.process.ProcessAdapter;
import com.intellij.execution.process.ProcessEvent;
import com.intellij.execution.process.ProcessHandler;
import com.intellij.openapi.util.Key;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Eugene.Kudelevsky
 */
public class AndroidProcessText {
  private static final Key<AndroidProcessText> KEY = new Key<AndroidProcessText>("ANDROID_PROCESS_TEXT");

  private final List<MyFragment> myFragments = new ArrayList<MyFragment>();

  private AndroidProcessText(@NotNull ProcessHandler processHandler) {
    processHandler.addProcessListener(new ProcessAdapter() {
      @Override
      public void onTextAvailable(ProcessEvent event, Key outputType) {
        myFragments.add(new MyFragment(event.getText(), outputType));
      }
    });
    processHandler.putUserData(KEY, this);
  }

  public static void attach(@NotNull ProcessHandler processHandler) {
    new AndroidProcessText(processHandler);
  }

  @Nullable
  public static AndroidProcessText get(@NotNull ProcessHandler processHandler) {
    return processHandler.getUserData(KEY);
  }

  public void printTo(@NotNull ProcessHandler processHandler) {
    for (MyFragment fragment : myFragments) {
      processHandler.notifyTextAvailable(fragment.getText(), fragment.getOutputType());
    }
  }

  private static class MyFragment {
    private final String myText;
    private final Key myOutputType;

    private MyFragment(@NotNull String text, @NotNull Key outputType) {
      myText = text;
      myOutputType = outputType;
    }

    @NotNull
    public String getText() {
      return myText;
    }

    @NotNull
    public Key getOutputType() {
      return myOutputType;
    }
  }
}
