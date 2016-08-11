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
package com.intellij.openapi.diff.impl.patch;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vcs.changes.CommitContext;
import com.intellij.openapi.vcs.changes.FilePathsHelper;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 10/17/11
 * Time: 7:35 PM
 */
public class CharsetEP implements PatchEP {
  private final static Key<Map<String, String>> ourName = Key.create("Charset");
  
  private final Project myProject;
  private final String myBaseDir;

  public CharsetEP(Project project) {
    myProject = project;
    myBaseDir = myProject.getBaseDir().getPath();
  }

  @NotNull
  @Override
  public String getName() {
    return "com.intellij.openapi.diff.impl.patch.CharsetEP";
  }

  @Override
  public CharSequence provideContent(@NotNull String path, CommitContext commitContext) {
    final File file = new File(myBaseDir, path);
    final VirtualFile vf = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(file);
    return vf == null ? null : vf.getCharset().name();
  }

  @Override
  public void consumeContent(@NotNull String path, @NotNull CharSequence content, CommitContext commitContext) {
  }

  @Override
  public void consumeContentBeforePatchApplied(@NotNull String path,
                                               @NotNull CharSequence content,
                                               CommitContext commitContext) {
    if (commitContext == null) return;
    Map<String, String> map = commitContext.getUserData(ourName);
    if (map == null) {
      map = new HashMap<>();
      commitContext.putUserData(ourName, map);
    }
    final File file = new File(myBaseDir, path);
    map.put(FilePathsHelper.convertPath(file.getPath()), content.toString());
  }
  
  public static String getCharset(final String path, final CommitContext commitContext) {
    if (commitContext == null) return null;
    final Map<String, String> userData = commitContext.getUserData(ourName);
    return userData == null ? null : userData.get(FilePathsHelper.convertPath(path));
  }
}
