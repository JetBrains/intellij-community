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
package org.jetbrains.plugins.gradle.integrations.maven;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.idea.maven.indices.MavenIndex;
import org.jetbrains.idea.maven.model.MavenRemoteRepository;

import java.util.Set;

/**
 * @author Vladislav.Soroka
 * @since 10/28/13
 */
public class MavenRepositoriesHolder {
  private volatile Set<MavenRemoteRepository> myRemoteRepositories;

  public MavenRepositoriesHolder() {
    myRemoteRepositories = ContainerUtil.newHashSet();
  }

  public static MavenRepositoriesHolder getInstance(Project p) {
    return p.getComponent(MavenRepositoriesHolder.class);
  }

  public void update(Set<MavenRemoteRepository> remoteRepositories) {
    myRemoteRepositories = ContainerUtil.newHashSet(remoteRepositories);
  }

  public Set<MavenRemoteRepository> getRemoteRepositories() {
    return myRemoteRepositories;
  }

  public boolean contains(String url) {
    final String pathOrUrl = MavenIndex.normalizePathOrUrl(url);
    for (MavenRemoteRepository repository : myRemoteRepositories) {
      if (MavenIndex.normalizePathOrUrl(repository.getUrl()).equals(pathOrUrl)) return true;
    }
    return false;
  }
}
