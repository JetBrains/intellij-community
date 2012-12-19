/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.history.wholeTree;

import com.google.common.collect.Sets;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import git4idea.history.browser.ChangesFilter;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author irengrig
 *         Date: 2/1/11
 *         Time: 7:27 PM
 */
public class GitLogFilters {
  @Nullable
  private final ChangesFilter.Comment myCommentFilter;
  @Nullable
  private final Set<ChangesFilter.Filter> myCommitterFilters;
  @Nullable
  private final Set<ChangesFilter.Filter> myDateFilters;
  @Nullable
  private final Map<VirtualFile, ChangesFilter.Filter> myStructureFilters;
  @Nullable
  private final List<String> myPossibleReferencies;
  private boolean myUseOnlyHashes;

  public GitLogFilters() {
    this(null, null, null, null, null);
  }

  public GitLogFilters(@Nullable ChangesFilter.Comment commentFilter,
                       @Nullable Set<ChangesFilter.Filter> committerFilters,
                       Set<ChangesFilter.Filter> filters,
                       @Nullable Map<VirtualFile, ChangesFilter.Filter> structureFilters,
                       @Nullable List<String> possibleReferencies) {
    myCommentFilter = commentFilter;
    myCommitterFilters = committerFilters;
    myDateFilters = filters;
    myStructureFilters = structureFilters;
    myPossibleReferencies = possibleReferencies;
  }

  public void callConsumer(final Consumer<List<ChangesFilter.Filter>> consumer, boolean takeComment, final VirtualFile root) {
    final List<Set<ChangesFilter.Filter>> filters = new ArrayList<Set<ChangesFilter.Filter>>();
    if (takeComment && myCommentFilter != null) {
      filters.add(Collections.<ChangesFilter.Filter, ChangesFilter.Filter>singletonMap(myCommentFilter, myCommentFilter).keySet());
    }
    if (myCommitterFilters != null) {
      filters.add(myCommitterFilters);
    }
    if (myStructureFilters != null) {
      final ChangesFilter.Filter filter = myStructureFilters.get(root);
      if (filter != null) {
        filters.add(Collections.singleton(filter));
      }
    }
    if (myDateFilters != null) {
      filters.add(Collections.<ChangesFilter.Filter>singleton(
          new ChangesFilter.And(myDateFilters.toArray(new ChangesFilter.Filter[myDateFilters.size()]))));
    }
    final Set<List<ChangesFilter.Filter>> cartesian = Sets.cartesianProduct(filters);
    if (cartesian.isEmpty()) {
      consumer.consume(Collections.<ChangesFilter.Filter>emptyList());
    } else {
      for (List<ChangesFilter.Filter> list : cartesian) {
        consumer.consume(list);
      }
    }
  }

  @Nullable
  public ChangesFilter.Comment getCommentFilter() {
    return myCommentFilter;
  }

  @Nullable
  public Set<ChangesFilter.Filter> getCommitterFilters() {
    return myCommitterFilters;
  }

  public boolean haveCommitterOrCommentFilters() {
    return (myCommitterFilters != null && ! myCommitterFilters.isEmpty()) || (myCommentFilter != null);
  }

  @Nullable
  public Map<VirtualFile,ChangesFilter.Filter> getStructureFilters() {
    return myStructureFilters;
  }

  public boolean isEmpty() {
    return myCommentFilter == null && (myCommitterFilters == null || myCommitterFilters.isEmpty()) &&
           (myStructureFilters == null || myStructureFilters.isEmpty()) && (myDateFilters == null || myDateFilters.isEmpty());
  }

  public boolean haveDisordering() {
    return ! isEmpty();
    // seems ordering by time does not coinside with structure -> dates filter also forbids tree. maybe should allow if "after" is used...
    /*return myCommentFilter != null || (myCommitterFilters != null && ! myCommitterFilters.isEmpty()) ||
           (myStructureFilters != null && ! myStructureFilters.isEmpty());*/
  }

  @Nullable
  public List<String> getPossibleReferencies() {
    return myPossibleReferencies;
  }

  public boolean haveStructureFilter() {
    return myStructureFilters != null;
  }

  public boolean haveStructuresForRoot(VirtualFile root) {
    return haveStructureFilter() && myStructureFilters.containsKey(root);
  }

  public boolean isUseOnlyHashes() {
    return myUseOnlyHashes;
  }

  public void setUseOnlyHashes(boolean useOnlyHashes) {
    myUseOnlyHashes = useOnlyHashes;
  }
}
