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
package git4idea.history.wholeTree;

import com.intellij.util.Consumer;
import com.intellij.util.containers.ReadonlyList;
import com.intellij.util.containers.SLRUMap;
import git4idea.history.browser.GitCommit;

/**
 * @author irengrig
 * todo can we specify some type?
 */
public class LinesProxy implements ReadonlyList<Object>, Consumer<GitCommit> {
  private final static int ourSize = 400; // todo ?
  private final ReadonlyList<VisibleLine> myTreeComposite;
  private final SLRUMap<String, GitCommit> myCache;

  public LinesProxy(final ReadonlyList<VisibleLine> treeComposite) {
    myTreeComposite = treeComposite;
    myCache = new SLRUMap<String, GitCommit>(ourSize, 50);
  }

  public boolean shouldLoad(int idx) {
    final VisibleLine visibleLine = myTreeComposite.get(idx);
    if (visibleLine.isDecoration()) {
      return false;
    }
    final String hash = new String(((TreeSkeletonImpl.Commit) visibleLine.getData()).getHash());
    final GitCommit gitCommit = myCache.get(hash);
    return gitCommit == null;
  }

  @Override
  public Object get(int idx) {
    final VisibleLine visibleLine = myTreeComposite.get(idx);
    if (visibleLine.isDecoration()) {
      return visibleLine.getData();
    }
    final String hash = new String(((TreeSkeletonImpl.Commit) visibleLine.getData()).getHash());
    // todo check that gets are made periodically while cell is visible; otherwise, ping
    // todo (check misses)
    final GitCommit gitCommit = myCache.get(hash);
    return (gitCommit == null) ? hash : gitCommit;
  }

  @Override
  public void consume(GitCommit gitCommit) {
    myCache.put(gitCommit.getShortHash(), gitCommit);
  }

  @Override
  public int getSize() {
    return myTreeComposite.getSize();
  }
}















































