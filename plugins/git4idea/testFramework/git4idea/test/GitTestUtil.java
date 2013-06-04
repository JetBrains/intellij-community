/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package git4idea.test;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.util.HashMap;
import java.util.Map;

import static com.intellij.dvcs.test.TestRepositoryUtil.createDir;
import static com.intellij.dvcs.test.TestRepositoryUtil.createFile;

/**
 * @author Kirill Likhodedov
 */
public class GitTestUtil {

  /**
   * <p>Creates file structure for given paths. Path element should be a relative (from project root)
   * path to a file or a directory. All intermediate paths will be created if needed.
   * To create a dir without creating a file pass "dir/" as a parameter.</p>
   * <p>Usage example:
   * <code>createFileStructure("a.txt", "b.txt", "dir/c.txt", "dir/subdir/d.txt", "anotherdir/");</code></p>
   * <p>This will create files a.txt and b.txt in the project dir, create directories dir, dir/subdir and anotherdir,
   * and create file c.txt in dir and d.txt in dir/subdir.</p>
   * <p>Note: use forward slash to denote directories, even if it is backslash that separates dirs in your system.</p>
   * <p>All files are populated with "initial content" string.</p>
   */
  public static Map<String, VirtualFile> createFileStructure(Project project, GitTestRepository repo, String... paths) {
    Map<String, VirtualFile> result = new HashMap<String, VirtualFile>();

    for (String path : paths) {
      String[] pathElements = path.split("/");
      boolean lastIsDir = path.endsWith("/");
      VirtualFile currentParent = repo.getVFRootDir();
      for (int i = 0; i < pathElements.length-1; i++) {
        currentParent = createDir(project, currentParent, pathElements[i]);
      }

      String lastElement = pathElements[pathElements.length-1];
      currentParent = lastIsDir ? createDir(project, currentParent, lastElement) : createFile(project, currentParent, lastElement, "content" + Math.random());
      result.put(path, currentParent);
    }
    return result;
  }

}
