// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcsUtil;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.AbstractVcs;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.rollback.DefaultRollbackEnvironment;
import com.intellij.openapi.vcs.rollback.RollbackEnvironment;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Unmodifiable;

import java.util.Collection;

import static java.util.Arrays.asList;

/**
 * @author Kirill Likhodedov
 */
public final class RollbackUtil {

  private RollbackUtil() {
  }


  /**
   * Finds the most appropriate name for the "Rollback" operation for the given VCSs.
   * That is: iterates through the all {@link RollbackEnvironment#getRollbackOperationName() RollbackEnvironments} and picks
   * the operation name if it is equal to all given VCSs.
   * Otherwise picks the {@link DefaultRollbackEnvironment#getRollbackOperationText() default name}.
   * @param vcses affected VCSs.
   * @return name for the "rollback" operation to be used in the UI.
   */
  public static @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getRollbackOperationName(@NotNull @Unmodifiable Collection<? extends AbstractVcs> vcses) {
    String operationName = null;
    for (AbstractVcs vcs : vcses) {
      final RollbackEnvironment rollbackEnvironment = vcs.getRollbackEnvironment();
      if (rollbackEnvironment != null) {
        if (operationName == null) {
          operationName = rollbackEnvironment.getRollbackOperationName();
        }
        else if (!operationName.equals(rollbackEnvironment.getRollbackOperationName())) {
          // if there are different names, use default
          return DefaultRollbackEnvironment.getRollbackOperationText();
        }
      }
    }
    return operationName != null ? operationName : DefaultRollbackEnvironment.getRollbackOperationText();
  }

  /**
   * Finds the appropriate name for the "rollback" operation, looking through all VCSs registered in the project.
   * @see #getRollbackOperationName(Collection)
   */
  public static @NotNull @Nls(capitalization = Nls.Capitalization.Title) String getRollbackOperationName(@NotNull Project project) {
    return getRollbackOperationName(asList(ProjectLevelVcsManager.getInstance(project).getAllActiveVcss()));
  }

}
