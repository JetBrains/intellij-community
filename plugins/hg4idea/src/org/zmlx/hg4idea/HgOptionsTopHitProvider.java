// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
final class HgOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@Nullable Project project) {
    if (project != null) {
      for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
        if ("Mercurial".equals(descriptor.getDisplayName())) {
          return Collections.unmodifiableCollection(Arrays.asList(
            option(project, "Mercurial: Check for incoming and outgoing changesets", "isCheckIncomingOutgoing", "setCheckIncomingOutgoing"),
            option(project, "Mercurial: Ignore whitespace differences in annotations", "isWhitespacesIgnoredInAnnotations", "setIgnoreWhitespacesInAnnotations")));
        }
      }
    }
    return Collections.emptyList();
  }

  private static BooleanOptionDescription option(final Project project, String option, String getter, String setter) {
    return new PublicMethodBasedOptionDescription(option, "vcs.Mercurial", getter, setter) {
      @Override
      public Object getInstance() {
        return ServiceManager.getService(project, HgProjectSettings.class);
      }
    };
  }
}
