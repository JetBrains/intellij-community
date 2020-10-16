// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.remote;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import org.jetbrains.annotations.NotNull;

/**
 * @author Kirill Likhodedov
 */
@State(
  name = "GitRememberedInputs",
  storages = @Storage("vcs.xml")
)
public class GitRememberedInputs extends DvcsRememberedInputs implements PersistentStateComponent<DvcsRememberedInputs.State> {

  @NotNull
  public static DvcsRememberedInputs getInstance() {
    return ApplicationManager.getApplication().getService(GitRememberedInputs.class);
  }
}
