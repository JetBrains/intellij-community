// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.remote;

import com.intellij.dvcs.hosting.RepositoryHostingService;
import com.intellij.openapi.extensions.ExtensionPointName;

public abstract class GitRepositoryHostingService implements RepositoryHostingService {
  public static final ExtensionPointName<GitRepositoryHostingService> EP_NAME =
    ExtensionPointName.create("Git4Idea.gitRepositoryHostingService");
}
