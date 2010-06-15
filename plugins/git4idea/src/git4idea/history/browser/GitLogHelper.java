/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package git4idea.history.browser;

import com.intellij.openapi.vcs.VcsException;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class GitLogHelper {
  private final LowLevelAccess myLowLevelAccess;

  public GitLogHelper(LowLevelAccess lowLevelAccess) {
    myLowLevelAccess = lowLevelAccess;
  }

  @NotNull
  public Portion loadPortion(final Collection<String> startingPoints, final Date beforePoint, final Date afterPoint,
                             final Collection<ChangesFilter.Filter> filtersIn, int maxCnt, List<String> branches) throws VcsException {
    final Collection<ChangesFilter.Filter> filters = new LinkedList<ChangesFilter.Filter>(filtersIn);
    if (beforePoint != null) {
      filters.add(new ChangesFilter.BeforeDate(new Date(beforePoint.getTime() - 1)));
    }
    if (afterPoint != null) {
      filters.add(new ChangesFilter.AfterDate(afterPoint));
    }

    final Portion portion = new Portion(null);
    myLowLevelAccess.loadCommits(startingPoints, Collections.<String>emptyList(), filters, portion, branches, maxCnt);
    return portion;
  }
}
