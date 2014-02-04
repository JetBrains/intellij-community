/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.model.data;

import com.intellij.openapi.externalSystem.model.Key;
import com.intellij.openapi.externalSystem.model.ProjectKeys;
import com.intellij.openapi.externalSystem.model.ProjectSystemId;
import com.intellij.openapi.externalSystem.model.project.AbstractExternalEntityData;
import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.Serializable;
import java.util.List;

/**
 * @author Vladislav.Soroka
 * @since 12/20/13
 */
public class BuildScriptClasspathData extends AbstractExternalEntityData {
  private static final long serialVersionUID = 1L;
  @NotNull
  public static final Key<BuildScriptClasspathData> KEY =
    Key.create(BuildScriptClasspathData.class, ProjectKeys.LIBRARY_DEPENDENCY.getProcessingWeight() + 1);

  @NotNull
  private final List<ClasspathEntry> myClasspathEntries;


  public BuildScriptClasspathData(@NotNull ProjectSystemId owner, @NotNull List<ClasspathEntry> classpathEntries) {
    super(owner);
    myClasspathEntries = classpathEntries;
  }

  @NotNull
  public List<ClasspathEntry> getClasspathEntries() {
    return myClasspathEntries;
  }

  public static class ClasspathEntry implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotNull
    private final File myClassesFile;

    @Nullable
    private final File mySourcesFile;

    @Nullable
    private final File myJavadocFile;

    public ClasspathEntry(@NotNull File classesFile, @Nullable File sourcesFile, @Nullable File javadocFile) {
      myClassesFile = classesFile;
      mySourcesFile = sourcesFile;
      myJavadocFile = javadocFile;
    }

    @NotNull
    public File getClassesFile() {
      return myClassesFile;
    }

    @Nullable
    public File getSourcesFile() {
      return mySourcesFile;
    }

    @Nullable
    public File getJavadocFile() {
      return myJavadocFile;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (!(o instanceof ClasspathEntry)) return false;

      ClasspathEntry entry = (ClasspathEntry)o;

      if (!FileUtil.filesEqual(myClassesFile, entry.myClassesFile)) return false;
      if (!FileUtil.filesEqual(myJavadocFile, entry.myJavadocFile)) return false;
      if (!FileUtil.filesEqual(mySourcesFile, entry.mySourcesFile)) return false;

      return true;
    }

    @Override
    public int hashCode() {
      int result = FileUtil.fileHashCode(myClassesFile);
      result = 31 * result + FileUtil.fileHashCode(mySourcesFile);
      result = 31 * result + FileUtil.fileHashCode(myJavadocFile);
      return result;
    }
  }
}
