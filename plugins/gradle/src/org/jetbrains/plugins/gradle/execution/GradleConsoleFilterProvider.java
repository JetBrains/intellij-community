/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.execution;

import com.intellij.execution.filters.ConsoleFilterProvider;
import com.intellij.execution.filters.Filter;
import com.intellij.execution.filters.RegexpFilter;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ProjectRootModificationTracker;
import com.intellij.openapi.util.io.OSAgnosticPathUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.util.CachedValue;
import com.intellij.psi.util.CachedValueProvider;
import com.intellij.util.CachedValueImpl;
import org.intellij.lang.annotations.Language;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.settings.GradleSettings;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.intellij.execution.filters.RegexpFilter.*;
import static java.lang.String.format;

public class GradleConsoleFilterProvider implements ConsoleFilterProvider {
  /*
   * `file:line` or `file:line:column`.
   */
  @Language("RegExp")
  private static final @NonNls String EXPRESSION = format("%s:%s(?::%s)?", FILE_PATH_MACROS, LINE_MACROS, COLUMN_MACROS);

  @Override
  public @NotNull Filter @NotNull [] getDefaultFilters(final @NotNull Project project) {
    return new Filter[]{
      new GradleConsoleFilter(project),
      new RegexpFilter(project, EXPRESSION) {
        private final CachedValue<@NotNull Boolean> myIsGradleProject = new CachedValueImpl<>(
          () -> CachedValueProvider.Result.create(isGradleProject(), ProjectRootModificationTracker.getInstance(project)));

        @Override
        public @Nullable Result applyFilter(final @NotNull String line, final int entireLength) {
          if (!OSAgnosticPathUtil.isAbsolute(line)) return null;
          if (!myIsGradleProject.getValue()) return null;
          final Result result = super.applyFilter(line, entireLength);
          if (result == null) return null;
          final Pattern pattern = getPattern();
          final Matcher matcher = pattern.matcher(StringUtil.newBombedCharSequence(line, 100));
          if (!matcher.lookingAt()) return result;
          final int lineStart = entireLength - line.length();
          final int start = lineStart + matcher.start();
          final int end = lineStart + matcher.end();
          return new Result(start, end, result.getFirstHyperlinkInfo());
        }

        private boolean isGradleProject() {
          return !GradleSettings.getInstance(project).getLinkedProjectsSettings().isEmpty();
        }
      },
    };
  }
}
