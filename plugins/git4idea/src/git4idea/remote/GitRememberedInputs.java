// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.RoamingType;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
@State(
  name = "GitRememberedInputs",
  storages = {
    @Storage(value = "vcs.xml", deprecated = true),
    @Storage(value = "vcs-inputs.xml", roamingType = RoamingType.DISABLED)
  }
)
public class GitRememberedInputs extends DvcsRememberedInputs implements PersistentStateComponent<DvcsRememberedInputs.State> {

  public static @NotNull DvcsRememberedInputs getInstance() {
    return ApplicationManager.getApplication().getService(GitRememberedInputs.class);
  }
}
