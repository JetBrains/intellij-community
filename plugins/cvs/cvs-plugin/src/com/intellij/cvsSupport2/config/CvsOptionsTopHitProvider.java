// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.cvsSupport2.config;

import com.intellij.CvsBundle;
import com.intellij.ide.ui.OptionsSearchTopHitProvider;
import com.intellij.ide.ui.PublicFieldBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
import com.intellij.ide.ui.search.OptionDescription;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vcs.ProjectLevelVcsManager;
import com.intellij.openapi.vcs.impl.VcsDescriptor;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;

/**
 * @author Sergey.Malenkov
 */
final class CvsOptionsTopHitProvider implements OptionsSearchTopHitProvider.ProjectLevelProvider {
  @NotNull
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<OptionDescription> getOptions(@NotNull Project project) {
    for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
      if ("CVS".equals(descriptor.getDisplayName())) {
        return Collections.unmodifiableCollection(Arrays.asList(
          option(project, "checkbox.use.read.only.flag.for.not.edited.files", "MAKE_NEW_FILES_READONLY"),
          option(project, "checkbox.show.cvs.server.output", "SHOW_OUTPUT")));
      }
    }
    return Collections.emptyList();
  }

  private static BooleanOptionDescription option(final Project project, String option, String field) {
    return new PublicFieldBasedOptionDescription("CVS: " + CvsBundle.message(option), "vcs.CVS", field) {
      @Override
      public Object getInstance() {
        return CvsConfiguration.getInstance(project);
      }
    };
  }
}
