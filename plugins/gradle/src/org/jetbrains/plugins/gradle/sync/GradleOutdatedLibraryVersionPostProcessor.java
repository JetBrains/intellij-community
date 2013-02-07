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
package org.jetbrains.plugins.gradle.sync;

import com.intellij.openapi.project.Project;
import com.intellij.util.SystemProperties;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitorAdapter;
import org.jetbrains.plugins.gradle.diff.dependency.GradleLibraryDependencyPresenceChange;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.diff.library.GradleOutdatedLibraryVersionChange;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryDependencyId;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId;
import org.jetbrains.plugins.gradle.util.GradleArtifactInfo;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * There is a following possible scenario:
 * <pre>
 * <ol>
 *   <li>A user imports a project from gradle (ide and gradle project are in sync now);</li>
 *   <li>New version is defined at build.gradle for particular library;</li>
 *   <li>Now we have two changes - gradle-local library (new version) and ide-local library (old version);</li>
 * </ol>
 * </pre>
 * It would be more convenient to provide a single change - 'outdated library' version. That gives a benefit that such a change
 * might be resolved in a single step instead of two (remove ide-local & import gradle-local).
 * <p/>
 * This post-processor checks changes and combines that ide- and gradle-local library versions changes into a single one.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/22/13 12:17 PM
 */
public class GradleOutdatedLibraryVersionPostProcessor implements GradleProjectStructureChangesPostProcessor {

  private static final boolean SKIP = SystemProperties.getBooleanProperty("gradle.skip.outdated.processing", false);

  @Override
  public void processChanges(@NotNull Collection<GradleProjectStructureChange> changes,
                             @NotNull Project project,
                             boolean onIdeProjectStructureChange)
  {
    if (SKIP) {
      return;
    }
    
    final Map<String /* library name */, Info> gradleData = ContainerUtilRt.newHashMap();
    final Map<String /* library name */, Info> ideData = ContainerUtilRt.newHashMap();
    final Set<GradleJarPresenceChange> jarPresenceChanges = ContainerUtilRt.newHashSet();

    //region Collect library dependency-local changes.
    GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleLibraryDependencyPresenceChange change) {
        Map<String /* library name */, Info> container = gradleData;
        GradleLibraryDependencyId libraryDependencyId = change.getGradleEntity();
        if (libraryDependencyId == null) {
          container = ideData;
          libraryDependencyId = change.getIdeEntity();
        }
        assert libraryDependencyId != null;
        GradleLibraryId libraryId = libraryDependencyId.getLibraryId();
        GradleArtifactInfo artifactInfo = GradleUtil.parseArtifactInfo(libraryId.getLibraryName());
        if (artifactInfo == null) {
          return;
        }
        String libraryName = artifactInfo.getName();
        String libraryVersion = artifactInfo.getVersion();
        if (libraryName == null || libraryVersion == null) {
          return;
        }
        Info info = container.get(libraryName);
        if (info == null) {
          container.put(libraryName, info = new Info(libraryId, libraryName, libraryVersion));
        }
        info.dependencyPresenceChanges.add(change);
      }

      @Override
      public void visit(@NotNull GradleJarPresenceChange change) {
        jarPresenceChanges.add(change);
      }
    };
    for (GradleProjectStructureChange change : changes) {
      change.invite(visitor);
    }
    //endregion

    Set<GradleLibraryId> libraryIds = ContainerUtilRt.newHashSet();

    //region Build library version change objects and forget ide- and gradle-local library dependency presence changes.
    for (Map.Entry<String, Info> entry : gradleData.entrySet()) {
      Info ideInfo = ideData.get(entry.getKey());
      if (ideInfo == null) {
        continue;
      }
      Info gradleInfo = entry.getValue();
      changes.add(new GradleOutdatedLibraryVersionChange(
        ideInfo.libraryName, gradleInfo.libraryId, gradleInfo.libraryVersion, ideInfo.libraryId, ideInfo.libraryVersion
      ));
      libraryIds.add(gradleInfo.libraryId);
      libraryIds.add(ideInfo.libraryId);
      
      // There is a possible situation that gradle project has more than one module which depend on the same library
      // but some of the corresponding ide modules doesn't have that library dependency at all. We want to show it as
      // gradle-local for it then. The same is true for the opposite situation (ide-local outdated library).
      Map<String /* module name */, GradleLibraryDependencyPresenceChange> gradleLibraryDependencies = ContainerUtilRt.newHashMap();
      for (GradleLibraryDependencyPresenceChange c : gradleInfo.dependencyPresenceChanges) {
        GradleLibraryDependencyId e = c.getGradleEntity();
        assert e != null;
        gradleLibraryDependencies.put(e.getOwnerModuleName(), c);
      }
      for (GradleLibraryDependencyPresenceChange c : ideInfo.dependencyPresenceChanges) {
        GradleLibraryDependencyId e = c.getIdeEntity();
        assert e != null;
        GradleLibraryDependencyPresenceChange gradleChange = gradleLibraryDependencies.remove(e.getOwnerModuleName());
        if (gradleChange != null) {
          changes.remove(gradleChange);
          changes.remove(c);
        }
      }
    }
    //endregion

    //region Drop information about jar presence changes for outdated libraries.
    for (GradleJarPresenceChange change : jarPresenceChanges) {
      GradleJarId jarId = change.getGradleEntity();
      if (jarId == null) {
        jarId = change.getIdeEntity();
      }
      assert jarId != null;
      if (libraryIds.contains(jarId.getLibraryId())) {
        changes.remove(change);
      }
    }
    //endregion
  }
  
  private static class Info {

    @NotNull public final Set<GradleLibraryDependencyPresenceChange> dependencyPresenceChanges = ContainerUtilRt.newHashSet();

    @NotNull public final GradleLibraryId libraryId;
    @NotNull public final String          libraryName;
    @NotNull public final String          libraryVersion;

    Info(@NotNull GradleLibraryId id, @NotNull String libraryName, @NotNull String libraryVersion) {
      libraryId = id;
      this.libraryName = libraryName;
      this.libraryVersion = libraryVersion;
    }
  }
}
