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
import com.intellij.util.containers.MultiMap;

import java.util.Collection;
import java.util.Set;

// when you're not at first page, or when your filter moved tags out, you might still want to filter by them
public class TagBridge {
  final Set<SHAHash> myParentsInterestedIn;
  private final LowLevelAccess myAccess;

  final MultiMap<SHAHash, String> myTagsForHashes;
  final MultiMap<String, SHAHash> myHashesForTags;

  public TagBridge(final Set<SHAHash> parentsInterestedIn, final LowLevelAccess access) {
    myParentsInterestedIn = parentsInterestedIn;
    myAccess = access;
    myTagsForHashes = new MultiMap<SHAHash, String>();
    myHashesForTags = new MultiMap<String, SHAHash>();
  }

  public void load() throws VcsException {
    for (SHAHash hash : myParentsInterestedIn) {
      final Collection<String> refs = myAccess.getBranchesWithCommit(hash);
      refs.addAll(myAccess.getTagsWithCommit(hash));

      myTagsForHashes.put(hash, refs);
      for (String ref : refs) {
        myHashesForTags.putValue(ref, hash);
      }
    }
  }

  // todo +-
  public MultiMap<String, SHAHash> getHashesForTags() {
    return myHashesForTags;
  }

  // todo +-
  public MultiMap<SHAHash, String> getTagsForHashes() {
    return myTagsForHashes;
  }
}
