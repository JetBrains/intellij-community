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
import com.intellij.openapi.roots.ex.ProjectRootManagerEx;
import com.intellij.util.containers.ContainerUtilRt;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitorAdapter;
import org.jetbrains.plugins.gradle.diff.library.GradleJarPresenceChange;
import org.jetbrains.plugins.gradle.manage.GradleJarManager;
import org.jetbrains.plugins.gradle.model.gradle.GradleJar;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.util.GradleArtifactInfo;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.*;

/**
 * {@link GradleProjectStructureChangesPostProcessor} implementation which adjusts library jars paths when it detects that they
 * have been moved.
 * <p/>
 * Target use-case: gradle uses gradle version-specific file system directory for holding downloaded library dependencies.
 * I.e. when we build project info with gradle of version X, it puts all jars into directory Dx. But when gradle version is
 * changed to Y, it will store all jars into directory Dy (different from Dx). So, following scenario is possible:
 * <pre>
 * <ol>
 *   <li>A user imports IJ project from gradle v.X;</li>
 *   <li>All necessary jars are downloaded and stored at Dx;</li>
 *   <li>A user switched gradle to version Y;</li>
 *   <li>
 *     When the project is refreshed we have a number of changes like 'gradle-local jar with location at Dy' and
 *     'intellij-local jar with location at Dx';
 *   </li>
 * </ol>
 * </pre>
 * We want to avoid that by auto-adjusting intellij library path config within the moved jars info.
 * <p/>
 * Thread-safe.
 * 
 * @author Denis Zhdanov
 * @since 1/16/13 6:19 PM
 */
public class GradleMovedJarsPostProcessor implements GradleProjectStructureChangesPostProcessor {
  
  @NotNull private final GradleJarManager myJarManager;

  public GradleMovedJarsPostProcessor(@NotNull GradleJarManager manager) {
    myJarManager = manager;
  }

  @Override
  public void processChanges(@NotNull final Collection<GradleProjectStructureChange> changes, @NotNull final Project project) {
    final Collection<MergeInfo> toMerge = buildMergeData(changes, project.getComponent(GradleProjectStructureContext.class));
    if (toMerge == null) {
      return;
    }
    
    Runnable mergeTask = new Runnable() {
      @Override
      public void run() {
        for (MergeInfo info : toMerge) {
          myJarManager.removeJars(Collections.singleton(info.ideJar), project);
          myJarManager.importJar(info.gradleJar, project);
          changes.removeAll(info.changes);
        }
      }
    };
    doMerge(mergeTask, project);
  }

  /**
   * This method is introduced in order to allow to cut 'Execute EDT/Execute under write action' etc stuff during test execution.
   * 
   * @param mergeTask  merge changes function object
   * @param project    target project
   */
  public void doMerge(@NotNull final Runnable mergeTask, @NotNull final Project project) {
    GradleUtil.executeProjectChangeAction(project, mergeTask, true, new Runnable() {
      @Override
      public void run() {
        ProjectRootManagerEx.getInstanceEx(project).mergeRootsChangesDuring(mergeTask);
      }
    });
  }
  
  @Nullable
  static Collection<MergeInfo> buildMergeData(@NotNull Collection<GradleProjectStructureChange> changes,
                                              @NotNull GradleProjectStructureContext context)
  {
    final Map<String, Set<GradleJarPresenceChange>> changesByLibrary = ContainerUtilRt.newHashMap();
    GradleProjectStructureChangeVisitor visitor = new GradleProjectStructureChangeVisitorAdapter() {
      @Override
      public void visit(@NotNull GradleJarPresenceChange change) {
        GradleJarId id = change.getIdeEntity();
        if (id == null) {
          id = change.getGradleEntity();
        }
        assert id != null;
        String libraryName = id.getLibraryId().getLibraryName();
        Set<GradleJarPresenceChange> c = changesByLibrary.get(libraryName);
        if (c == null) {
          changesByLibrary.put(libraryName, c = ContainerUtilRt.newHashSet());
        }
        c.add(change);
      }
    };
    for (GradleProjectStructureChange change : changes) {
      change.invite(visitor);
    }

    final Collection<MergeInfo> toMerge = ContainerUtilRt.newArrayList();
    for (Set<GradleJarPresenceChange> c : changesByLibrary.values()) {
      processLibraryJarChanges(c, context, toMerge);
    }
    if (toMerge.isEmpty()) {
      return null;
    }
    return toMerge;
  }

  private static void processLibraryJarChanges(@NotNull Set<GradleJarPresenceChange> changes,
                                               @NotNull GradleProjectStructureContext context,
                                               @NotNull Collection<MergeInfo> toMerge)
  {
    Map<LibraryPathType, Map<GradleArtifactInfo, GradleJarPresenceChange>> gradleLocalJars = ContainerUtilRt.newHashMap();
    Map<LibraryPathType, Map<GradleArtifactInfo, GradleJarPresenceChange>> ideLocalJars = ContainerUtilRt.newHashMap();
    for (GradleJarPresenceChange change : changes) {
      Map<LibraryPathType, Map<GradleArtifactInfo, GradleJarPresenceChange>> storageToUse = gradleLocalJars;
      GradleJarId entity = change.getGradleEntity();
      if (entity == null) {
        entity = change.getIdeEntity();
        assert entity != null;
        storageToUse = ideLocalJars;
      }
      GradleArtifactInfo artifactInfo = GradleUtil.parseArtifactInfo(entity.getPath());
      if (artifactInfo != null) {
        Map<GradleArtifactInfo, GradleJarPresenceChange> m = storageToUse.get(entity.getLibraryPathType());
        if (m == null) {
          storageToUse.put(entity.getLibraryPathType(), m = ContainerUtilRt.newHashMap());
        }
        m.put(artifactInfo, change);
      }
    }

    for (Map.Entry<LibraryPathType, Map<GradleArtifactInfo, GradleJarPresenceChange>> entry : gradleLocalJars.entrySet()) {
      for (GradleArtifactInfo info : entry.getValue().keySet()) {
        Map<GradleArtifactInfo, GradleJarPresenceChange> m = ideLocalJars.get(entry.getKey());
        if (m == null) {
          continue;
        }
        GradleJarPresenceChange ideLocalJarChange = m.get(info);
        if (ideLocalJarChange == null) {
          continue;
        }
        GradleJarId ideJarId = ideLocalJarChange.getIdeEntity();
        assert ideJarId != null;
        GradleJar ideJar = ideJarId.mapToEntity(context);
        if (ideJar == null) {
          continue;
        }

        GradleJarPresenceChange gradleLocalJarChange = entry.getValue().get(info);
        GradleJarId gradleJarId = gradleLocalJarChange.getGradleEntity();
        assert gradleJarId != null;
        GradleJar gradleJar = gradleJarId.mapToEntity(context);
        if (gradleJar == null) {
          continue;
        }

        toMerge.add(new MergeInfo(gradleJar, ideJar, gradleLocalJarChange, ideLocalJarChange));
      }
    }
  }
  
  static class MergeInfo {

    @NotNull public final Collection<GradleJarPresenceChange> changes = ContainerUtilRt.newArrayList();

    @NotNull public final GradleJar gradleJar;
    @NotNull public final GradleJar ideJar;

    MergeInfo(@NotNull GradleJar gradleJar, @NotNull GradleJar ideJar, GradleJarPresenceChange... changes) {
      this.gradleJar = gradleJar;
      this.ideJar = ideJar;
      this.changes.addAll(Arrays.asList(changes));
    }

    @Override
    public String toString() {
      return String.format("jar '%s' for library '%s'", gradleJar.getName(), gradleJar.getLibraryId().getLibraryName());
    }
  }
}
