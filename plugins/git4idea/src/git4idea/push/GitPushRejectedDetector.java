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
package git4idea.push;

import com.intellij.openapi.util.Key;
import git4idea.commands.GitLineHandlerListener;

import java.util.ArrayList;
import java.util.Collection;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Kirill Likhodedov
 */
class GitPushRejectedDetector implements GitLineHandlerListener {

  private static final Pattern REJECTED_PATTERN = Pattern.compile("\\s+! \\[rejected\\]\\s+(\\S+) -> (\\S+) .*");
  
  private final Collection<RejectedRef> myRejectedRefs = new ArrayList<RejectedRef>();

  @Override
  public void onLineAvailable(String line, Key outputType) {
    Matcher matcher = REJECTED_PATTERN.matcher(line);
    if (matcher.matches()) {
      String src = matcher.group(1);
      String dst = matcher.group(2);
      myRejectedRefs.add(new RejectedRef(src, dst));
    }
  }

  @Override
  public void processTerminated(int exitCode) {
  }

  @Override
  public void startFailed(Throwable exception) {
  }
  
  public boolean rejected() {
    return !myRejectedRefs.isEmpty();
  }

  public Collection<String> getRejectedBranches() {
    Collection<String> branches = new ArrayList<String>(myRejectedRefs.size());
    for (RejectedRef rejectedRef : myRejectedRefs) {
      branches.add(rejectedRef.mySource);
    }
    return branches;
  }

  static class RejectedRef {
    private final String mySource;
    private final String myDestination;

    RejectedRef(String source, String destination) {
      myDestination = destination;
      mySource = source;
    }
  }
}
