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

import com.intellij.util.AsynchConsumer;
import com.intellij.util.Processor;
import com.intellij.util.containers.MultiMap;
import org.jetbrains.annotations.Nullable;

import java.util.*;

// todo introduce synchronization here?
public class Portion implements AsynchConsumer<GitCommit> {
  private final Map<String, SHAHash> myNameToHash;
  private final Map<String, Integer> myHolder;
  private boolean myStartFound;

  // ordered
  private List<GitCommit> myOrdered;

  private final boolean myChildrenWasSet;

  private final MultiMap<String, GitCommit> myOrphanMap;

  private final Set<String> myUsers;

  // parents, w/out loaded commits; theoretically, those in myOrphan map todo check
  private final List<GitCommit> myRoots;
  // what was passed into log command
  private final List<GitCommit> myLeafs;
  @Nullable private final List<SHAHash> myStartingPoints;

  public Portion(@Nullable final List<SHAHash> startingPoints) {
    myStartingPoints = startingPoints;
    myChildrenWasSet = startingPoints == null || startingPoints.isEmpty();
    
    myNameToHash = new HashMap<String, SHAHash>();
    myHolder = new HashMap<String, Integer>();
    myOrdered = new LinkedList<GitCommit>();
    
    myRoots = new LinkedList<GitCommit>();
    myLeafs = new LinkedList<GitCommit>();

    myOrphanMap = new MultiMap<String, GitCommit>();
    myUsers = new HashSet<String>();
  }

  public void finished() {
    //To change body of implemented methods use File | Settings | File Templates.
  }

  public Set<String> getUsers() {
    return myUsers;
  }

  public void consume(final GitCommit commit) {
    myUsers.add(commit.getAuthor());
    myUsers.add(commit.getCommitter());

    myOrdered.add(commit);
    myHolder.put(commit.getShortHash(), myOrdered.size() - 1);
    final Collection<GitCommit> orphans = myOrphanMap.get(commit.getShortHash());
    for (GitCommit orphan : orphans) {
      orphan.addParentLink(commit);
    }

    final List<String> referencies = commit.getBranches();
    if (! referencies.isEmpty()) {
      final SHAHash hash = commit.getHash();
      for (String reference : referencies) {
        myNameToHash.put(reference, hash);
      }
    }
    final List<String> tags = commit.getTags();
    if (! tags.isEmpty()) {
      final SHAHash hash = commit.getHash();
      for (String reference : tags) {
        myNameToHash.put(reference, hash);
      }
    }

    final Set<String> parentHashes = commit.getParentsHashes();
    if (parentHashes.isEmpty()) {
      myStartFound = true;
    } else {
      for (String parentHash : parentHashes) {
        final Integer idx = myHolder.get(parentHash);
        if (idx != null) {
          final GitCommit parent = myOrdered.get(idx);
          commit.addParentLink(parent);
        } else {
          myOrphanMap.putValue(parentHash, commit);
        }
      }
    }
  }

  public boolean isStartFound() {
    return myStartFound;
  }

  @Nullable
  public GitCommit getLast() {
    return myOrdered.isEmpty() ? null : myOrdered.get(myOrdered.size() - 1);
  }

  // todo make simplier
  public List<GitCommit> getXFrom(final int idx, final int num) {
    final List<GitCommit> result = new ArrayList<GitCommit>();
    iterateFrom(idx, new Processor<GitCommit>() {
      public boolean process(GitCommit gitCommit) {
        result.add(gitCommit);
        return result.size() == num;
      }
    });
    return result;
  }

  public void iterateFrom(final int idx, final Processor<GitCommit> processor) {
    if ((idx < 0) || (idx > (myOrdered.size() - 1))) return;

    for (int i = idx; i < myOrdered.size(); i++) {
      final GitCommit commit = myOrdered.get(i);
      if (processor.process(commit)) return;
    }
  }

  public SHAHash getHashForReference(final String reference) {
    return myNameToHash.get(reference);
  }

  @Nullable
  public GitCommit getByHash(final String hash) {
    final SHAHash shaHash = new SHAHash(hash);
    final Integer idx = myHolder.get(shaHash);
    return idx == null ? null : myOrdered.get(idx);
  }
}
