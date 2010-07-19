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
package org.jetbrains.plugins.groovy.dsl;

import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.PathUtil;
import com.intellij.util.indexing.IndexableSetContributor;

import java.io.File;
import java.util.Collections;
import java.util.Set;

public class AbstractDslIndexedRootsProvider extends IndexableSetContributor implements GroovyDslIndexedRootProvider{
  private final Set<VirtualFile> ourDslsDirs;

  public AbstractDslIndexedRootsProvider() {
    final File jarPath = new File(PathUtil.getJarPathForClass(getClass()));
    String dirPath;
    if (jarPath.isFile()) { //jar
      dirPath = new File(jarPath.getParentFile(), getScriptFolderName()).getAbsolutePath();
    } else {
      dirPath = new File(jarPath, getScriptFolderName()).getAbsolutePath();
    }

    final VirtualFile parent = LocalFileSystem.getInstance().refreshAndFindFileByPath(dirPath);
    assert parent != null : dirPath;
    parent.getChildren();
    ourDslsDirs = Collections.singleton(parent);
    parent.refresh(true, true);
  }

  protected String getScriptFolderName() {
    return "standardDsls";
  }

  @Override
  public Set<VirtualFile> getAdditionalRootsToIndex() {
    return ourDslsDirs;
  }
}
