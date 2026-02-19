// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.serialization.PropertyMapping;
import com.intellij.util.containers.Interner;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.DependencyAccessorsModel;
import org.jetbrains.plugins.gradle.model.VersionCatalogsModel;

import java.io.File;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class BuildScriptClasspathData extends AbstractExternalEntityData {
  public static final @NotNull Key<BuildScriptClasspathData> KEY =
    Key.create(BuildScriptClasspathData.class, ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1);
  public static final Key<VersionCatalogsModel> VERSION_CATALOGS = Key.create(VersionCatalogsModel.class, KEY.getProcessingWeight());
  public static final Key<DependencyAccessorsModel> ACCESSORS = Key.create(DependencyAccessorsModel.class, KEY.getProcessingWeight());

  private @Nullable File gradleHomeDir;

  private final @NotNull List<ClasspathEntry> classpathEntries;

  @PropertyMapping({"owner", "classpathEntries"})
  public BuildScriptClasspathData(@NotNull ProjectSystemId owner, @NotNull List<ClasspathEntry> classpathEntries) {
    super(owner);

    this.classpathEntries = classpathEntries;
  }

  public @Nullable File getGradleHomeDir() {
    return gradleHomeDir;
  }

  public void setGradleHomeDir(@Nullable File gradleHomeDir) {
    this.gradleHomeDir = gradleHomeDir;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;
    BuildScriptClasspathData data = (BuildScriptClasspathData)o;
    return Objects.equals(gradleHomeDir, data.gradleHomeDir) &&
           classpathEntries.equals(data.classpathEntries);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), gradleHomeDir, classpathEntries);
  }

  public @NotNull List<ClasspathEntry> getClasspathEntries() {
    return classpathEntries;
  }

  public static final class ClasspathEntry {

    private static final Interner<ClasspathEntry> ourEntryInterner = Interner.createWeakInterner();

    private final @NotNull Set<String> classesFile;

    private final @NotNull Set<String> sourcesFile;

    private final @NotNull Set<String> javadocFile;

    public static ClasspathEntry create(@NotNull Set<String> classesFile,
                                        @NotNull Set<String> sourcesFile,
                                        @NotNull Set<String> javadocFile) {
      return ourEntryInterner.intern(new ClasspathEntry(classesFile, sourcesFile, javadocFile));
    }


    /**
     * @deprecated use ClasspathEntry{@link #create(Set, Set, Set)} to avoid memory leaks
     */
    @Deprecated(forRemoval = true)
    @PropertyMapping({"classesFile", "sourcesFile", "javadocFile"})
    public ClasspathEntry(@NotNull Set<String> classesFile, @NotNull Set<String> sourcesFile, @NotNull Set<String> javadocFile) {
      this.classesFile = classesFile;
      this.sourcesFile = sourcesFile;
      this.javadocFile = javadocFile;
    }

    public @NotNull Set<String> getClassesFile() {
      return classesFile;
    }

    public @NotNull Set<String> getSourcesFile() {
      return sourcesFile;
    }

    public @NotNull Set<String> getJavadocFile() {
      return javadocFile;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClasspathEntry entry)) return false;

      if (!classesFile.equals(entry.classesFile)) return false;
      if (!javadocFile.equals(entry.javadocFile)) return false;
      if (!sourcesFile.equals(entry.sourcesFile)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = classesFile.hashCode();
      result = 31 * result + sourcesFile.hashCode();
      result = 31 * result + javadocFile.hashCode();
      return result;
    }
  }
}
