/*
 * Copyright 2000-2014 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.cvsSupport2.config;

import com.intellij.CvsBundle;
import com.intellij.ide.ui.OptionsTopHitProvider;
import com.intellij.ide.ui.PublicFieldBasedOptionDescription;
import com.intellij.ide.ui.search.BooleanOptionDescription;
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
public final class CvsOptionsTopHitProvider extends OptionsTopHitProvider {
  @Override
  public String getId() {
    return "vcs";
  }

  @NotNull
  @Override
  public Collection<BooleanOptionDescription> getOptions(@Nullable Project project) {
    if (project != null) {
      for (VcsDescriptor descriptor : ProjectLevelVcsManager.getInstance(project).getAllVcss()) {
        if ("CVS".equals(descriptor.getDisplayName())) {
          return Collections.unmodifiableCollection(Arrays.asList(
            option(project, "checkbox.use.read.only.flag.for.not.edited.files", "MAKE_NEW_FILES_READONLY"),
            option(project, "checkbox.show.cvs.server.output", "SHOW_OUTPUT")));
        }
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
