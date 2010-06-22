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

import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;

public interface GitTreeFiltering {
  void addFilter(final ChangesFilter.Filter filter);
  void removeFilter(final ChangesFilter.Filter filter);

  void addStartingPoint(final String ref);
  void removeStartingPoint(final String ref);

  void updateExcludePoints(final List<String> points);

  void markDirty();

  // todo COPIES!
  @Nullable
  Collection<String> getStartingPoints();
  @Nullable
  List<String> getExcludePoints();
  Collection<ChangesFilter.Filter> getFilters();

}
