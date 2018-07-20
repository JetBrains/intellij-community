// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.remote;

import com.intellij.openapi.project.Project;
import com.intellij.util.AuthData;
import org.jetbrains.annotations.CalledInAwt;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * Provides authentication information to the {@link git4idea.commands.GitHttpAuthenticator} for a single run.
 * Main use-case is to show a button in git login dialog which will allow user to manually select data that will be used for the current run.
 * Generally it will be shown when {@link GitHttpAuthDataProvider} could not provide appropriate auth data silently.
 *
 * It is advised to configure related {@link GitHttpAuthDataProvider} for the continuous use when data is selected.
 *
 * @see GitRepositoryHostingService#getSessionAuthDataProvider(Project, String)
 * @see GitRepositoryHostingService#getSessionAuthDataProvider(Project, String, String)
 */
public interface GitHttpSessionAuthDataProvider {
  @CalledInAwt
  @Nullable
  AuthData getAuthData(@Nullable JComponent parentComponent);
}
