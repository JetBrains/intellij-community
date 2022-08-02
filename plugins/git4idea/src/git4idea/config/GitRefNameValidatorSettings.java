// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.config;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

import java.util.stream.IntStream;

@State(
  name = "git4idea.config.GitRefNameValidatorSettings",
  storages = @Storage("GitRefNameValidatorSettings.xml")
)
public class GitRefNameValidatorSettings
  implements PersistentStateComponent<GitRefNameValidatorSettings.State>, GitRefNameValidatorSettingsInterface {
  public static final IntStream MAX_NUM_OF_CONSECUTIVE_UNDERSCORES_OPTIONS = IntStream.range(1, 5);

  public static class State {
    public boolean isOn = true;
    public @NotNull GitRefNameValidatorReplacementOption replacementOption = GitRefNameValidatorReplacementOption.getDefault();
    public boolean isConvertingToLowerCase = false;
    public int maxNumberOfConsecutiveUnderscores = 1;
  }

  @NotNull
  public static GitRefNameValidatorSettingsInterface getInstance() {
    return ApplicationManager.getApplication().getService(GitRefNameValidatorSettings.class);
  }

  @NotNull
  protected State myState = new State();

  @Override
  public @NotNull State getState() {
    return myState;
  }

  @Override
  public void loadState(@NotNull State state) {
    myState = state;
  }

  @Override
  public boolean isOn() {
    return myState.isOn;
  }

  @Override
  public void setOn(boolean isOn) {
    myState.isOn = isOn;
  }

  @Override
  public @NotNull GitRefNameValidatorReplacementOption getReplacementOption() {
    return myState.replacementOption;
  }

  @Override
  public void setReplacementOption(GitRefNameValidatorReplacementOption replacementOption) {
    myState.replacementOption = replacementOption;
  }

  @Override
  public boolean isConvertingToLowerCase() {
    return myState.isConvertingToLowerCase;
  }

  @Override
  public void setConvertingToLowerCase(boolean isConvertingToLowerCase) {
    myState.isConvertingToLowerCase = isConvertingToLowerCase;
  }

  @Override
  public int getMaxNumberOfConsecutiveUnderscores() {
    return myState.maxNumberOfConsecutiveUnderscores;
  }

  @Override
  public void setMaxNumberOfConsecutiveUnderscores(int maxNumberOfConsecutiveUnderscores) {
    myState.maxNumberOfConsecutiveUnderscores = maxNumberOfConsecutiveUnderscores;
  }
}
