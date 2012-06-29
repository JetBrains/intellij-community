/*
 * Copyright 2000-2010 JetBrains s.r.o.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package git4idea.history.wholeTree;

import com.intellij.openapi.vfs.VirtualFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @author irengrig
 *
 * to support safe root numbering
 * will be created in UI in roots listener
 */
public class RootsHolder {
  private final List<VirtualFile> myRoots;

  public RootsHolder(final List<VirtualFile> roots) {
    myRoots = Collections.unmodifiableList(new ArrayList<VirtualFile>(roots));
  }

  public VirtualFile get(final int i) {
    return myRoots.get(i);
  }

  public List<VirtualFile> getRoots() {
    return myRoots;
  }

  public boolean multipleRoots() {
    return myRoots.size() > 1;
  }
}
