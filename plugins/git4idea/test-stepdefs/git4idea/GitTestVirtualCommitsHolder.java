/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea;

import java.util.HashMap;
import java.util.Map;

/**
 * So called Virtual Commits are used in Cucumber feature files, so that the writer of a test could refer to commits in the natural way:
 * via hashes. We can't define a hash to the commit when committing to Git, therefore to match these two sets we store them here.
 *
 * @author Kirill Likhodedov
 */
public class GitTestVirtualCommitsHolder {

  // virtual hash -> commit details
  private Map<String, CommitDetails> commits = new HashMap<>();

  void register(String virtualHash, CommitDetails CommitInfo) {
    commits.put(virtualHash, CommitInfo);
  }

  CommitDetails getRealCommit(String virtualHash) {
    return commits.get(virtualHash);
  }

  String replaceVirtualHashes(String message) {
    for (Map.Entry<String, CommitDetails> entry : commits.entrySet()) {
      message = message.replace(entry.getKey(), entry.getValue().getHash());
    }
    return message;
  }

}
