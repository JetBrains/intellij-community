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
package git4idea.roots;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collection;

/**
 * The result of root detection.
 *
 * @author Kirill Likhodedov
 */
public class GitRootDetectInfo {

  private final @NotNull Collection<VirtualFile> myRoots;
  private final boolean myFull;
  private final boolean myBelow;

  /**
   * @param roots Git roots important for the project.
   * @param full  Pass true to indicate that the project is fully under Git.
   * @param below Pass true to indicate that the project dir is below Git dir,
   *              i.e. .git is above the project dir, and there is no .git directly under the project dir.
   */
  GitRootDetectInfo(@NotNull Collection<VirtualFile> roots, boolean full, boolean below) {
    myRoots = new ArrayList<VirtualFile>(roots);
    myFull = full;
    myBelow = below;
  }

  /**
   * @return True if the project is fully under Git.
   * It is true if .git is directly inside or above the project dir.
   */
  boolean totallyUnderGit() {
    return myFull;
  }

  boolean empty() {
    return myRoots.isEmpty();
  }

  @NotNull
  public Collection<VirtualFile> getRoots() {
    return new ArrayList<VirtualFile>(myRoots);
  }

  /**
   * Below implies totally under Git.
   * @return true if the uppermost interesting Git root is above the project dir,
   *         false if all .git directories are immediately under the project dir or deeper.
   */
  boolean projectIsBelowGit() {
    return myBelow;
  }

}
