/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.NonClasspathClassFinder;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.config.GroovyFacetUtil;

import java.util.Collections;
import java.util.List;

public class GroovyBundledClassFinder extends NonClasspathClassFinder {

  public GroovyBundledClassFinder(Project project) {
    super(project);
  }

  @Override
  protected List<VirtualFile> calcClassRoots() {
    return getBundledGroovyJarRoots();
  }

  @NotNull
  public static List<VirtualFile> getBundledGroovyJarRoots() {
    final VirtualFile bundledJar = LocalFileSystem.getInstance().findFileByIoFile(GroovyFacetUtil.getBundledGroovyJar());
    if (bundledJar != null) {
      final VirtualFile root = StandardFileSystems.getJarRootForLocalFile(bundledJar);
      if (root != null) {
        return Collections.singletonList(root);
      }
    }
    return Collections.emptyList();
  }
}
