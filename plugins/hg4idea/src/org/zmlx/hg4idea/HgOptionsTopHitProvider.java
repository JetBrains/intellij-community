// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.zmlx.hg4idea;

import com.intellij.ide.ui.PublicMethodBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.configurable.VcsOptionsTopHitProviderBase;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

final class HgOptionsTopHitProvider extends VcsOptionsTopHitProviderBase {
  @NotNull
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    if (isEnabled(project, HgVcs.getInstance(project))) {
      return Collections.unmodifiableCollection(Arrays.asList(
        option(project, "Mercurial: Check for incoming and outgoing changesets", "isCheckIncomingOutgoing", "setCheckIncomingOutgoing"),
        option(project, "Mercurial: Ignore whitespace differences in annotations", "isWhitespacesIgnoredInAnnotations",
               "setIgnoreWhitespacesInAnnotations")));
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
