/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.grape;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider;
import com.intellij.openapi.roots.SyntheticLibrary;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.indexing.IndexableSetContributor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.lang.resolve.GrabService;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author Dmitry Avdeev
 */
public class GrabRootsProvider extends AdditionalLibraryRootsProvider {
  @NotNull
  @Override
  public Collection<SyntheticLibrary> getAdditionalProjectLibraries(@NotNull Project project) {
    Set<VirtualFile> dependencies = GrabService.getInstance(project).getJars();
    return Collections.singletonList(new GrabSyntheticLibrary(dependencies));
  }

  public static class GrabSyntheticLibrary extends SyntheticLibrary {
    private final Set<VirtualFile> files ;

    public GrabSyntheticLibrary(Set<VirtualFile> files) {
      this.files = files;
    }

    @NotNull
    @Override
    public Collection<VirtualFile> getSourceRoots() {
      return files;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      GrabSyntheticLibrary library = (GrabSyntheticLibrary)o;

      if (files != null ? !files.equals(library.files) : library.files != null) return false;

      return true;
    }

    @Override
    public int hashCode() {
      return files != null ? files.hashCode() : 0;
    }
  }
  //@NotNull
  //@Override
  //public Set<VirtualFile> getAdditionalProjectRootsToIndex(@NotNull Project project) {
  //  Set<VirtualFile> dependencies = GrabService.getInstance(project).getJars();//(GlobalSearchScope.allScope(project)));
  //  GrabServiceImpl.LOG.info("GrabRootsProvider  roots " + String.join(",", dependencies.stream().map(String::valueOf).collect(
  //    Collectors.toList())));
  //  return dependencies;
  //}
  //
  //@NotNull
  //@Override
  //public Set<VirtualFile> getAdditionalRootsToIndex() {
  //  return Collections.emptySet();
  //}
}
