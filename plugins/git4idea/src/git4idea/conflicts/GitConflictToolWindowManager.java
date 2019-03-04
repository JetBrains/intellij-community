// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts;

import com.intellij.openapi.components.ProjectComponent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentI;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.annotations.NotNull;

public class GitConflictToolWindowManager implements ProjectComponent {
  @NotNull private final Project myProject;
  @NotNull private final ChangesViewContentI myContentManager;

  public GitConflictToolWindowManager(@NotNull Project project, @NotNull ChangesViewContentI contentManager) {
    myProject = project;
    myContentManager = contentManager;
  }

  @Override
  public void projectOpened() {
    final String displayName = "Conflicts";

    GitConflictsView panel = new GitConflictsView(myProject);

    Content content = ContentFactory.SERVICE.getInstance().createContent(panel.getComponent(), displayName, false);
    content.setPreferredFocusedComponent(() -> panel.getPreferredFocusableComponent());
    myContentManager.addContent(content);
  }
}
