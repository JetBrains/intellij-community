/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.tools.idea.gradle.dsl.model.dependencies;

import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencyModel;
import com.android.tools.idea.gradle.dsl.api.dependencies.ArtifactDependencySpec;
import com.google.common.base.Joiner;
import com.google.common.base.Objects;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

import static com.android.tools.idea.gradle.dsl.GradleUtil.GRADLE_PATH_SEPARATOR;
import static com.google.common.base.Strings.emptyToNull;
import static com.intellij.openapi.util.text.StringUtil.isNotEmpty;

public class ArtifactDependencySpecImpl implements ArtifactDependencySpec {
  @NotNull private String name;

  @Nullable private String group;
  @Nullable private String version;
  @Nullable private String classifier;
  @Nullable private String extension;

  @Nullable
  public static ArtifactDependencySpecImpl create(@NotNull String notation) {
    // Example: org.gradle.test.classifiers:service:1.0:jdk15@jar where
    //   group: org.gradle.test.classifiers
    //   name: service
    //   version: 1.0
    //   classifier: jdk15
    //   extension: jar
    List<String> segments = Splitter.on(GRADLE_PATH_SEPARATOR).trimResults().omitEmptyStrings().splitToList(notation);
    int segmentCount = segments.size();
    if (segmentCount > 0) {
      segments = Lists.newArrayList(segments);
      String lastSegment = segments.remove(segmentCount - 1);
      String extension = null;
      int indexOfAt = lastSegment.indexOf('@');
      if (indexOfAt != -1) {
        extension = lastSegment.substring(indexOfAt + 1);
        lastSegment = lastSegment.substring(0, indexOfAt);
      }
      segments.add(lastSegment);
      segmentCount = segments.size();

      String group = null;
      String name = null;
      String version = null;
      String classifier = null;

      if (segmentCount == 2) {
        if (!lastSegment.isEmpty() && Character.isDigit(lastSegment.charAt(0))) {
          name = segments.get(0);
          version = lastSegment;
        }
        else {
          group = segments.get(0);
          name = segments.get(1);
        }
      }
      else if (segmentCount == 3 || segmentCount == 4) {
        group = segments.get(0);
        name = segments.get(1);
        version = segments.get(2);
        if (segmentCount == 4) {
          classifier = segments.get(3);
        }
      }
      if (isNotEmpty(name)) {
        return new ArtifactDependencySpecImpl(name, group, version, classifier, extension);
      }
    }
    return null;
  }

  @Override
  @NotNull
  public String getName() {
    return name;
  }

  @Override
  @Nullable
  public String getGroup() {
    return group;
  }

  @Override
  @Nullable
  public String getVersion() {
    return version;
  }

  @Nullable
  @Override
  public String getClassifier() {
    return classifier;
  }

  @Override
  @Nullable
  public String getExtension() {
    return extension;
  }

  public void setName(@NotNull String newName) {
    name = newName;
  }

  public void setGroup(@Nullable String newGroup) {
    group = newGroup;
  }

  public void setVersion(@Nullable String newVersion) {
    version = newVersion;
  }

  public void setClassifier(@Nullable String newClassifier) {
    classifier = newClassifier;
  }

  public void setExtension(@Nullable String newExtension) {
    extension = newExtension;
  }

  @NotNull
  public static ArtifactDependencySpec create(@NotNull ArtifactDependencyModel dependency) {
    String name = dependency.name().toString();
    assert name != null;
    return new ArtifactDependencySpecImpl(name,
                                          dependency.group().toString(),
                                          dependency.version().toString(),
                                          dependency.classifier().toString(),
                                          dependency.extension().toString());
  }

  public ArtifactDependencySpecImpl(@NotNull String name, @Nullable String group, @Nullable String version) {
    this(name, group, version, null, null);
  }

  public ArtifactDependencySpecImpl(@NotNull String name,
                                    @Nullable String group,
                                    @Nullable String version,
                                    @Nullable String classifier,
                                    @Nullable String extension) {
    this.name = name;
    this.group = emptyToNull(group);
    this.version = emptyToNull(version);
    this.classifier = emptyToNull(classifier);
    this.extension = emptyToNull(extension);
  }

  @Override
  public boolean equalsIgnoreVersion(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ArtifactDependencySpecImpl that = (ArtifactDependencySpecImpl)o;
    return Objects.equal(name, that.name) &&
           Objects.equal(group, that.group) &&
           Objects.equal(classifier, that.classifier) &&
           Objects.equal(extension, that.extension);
  }

  @Override
  public boolean equals(Object o) {
    if (equalsIgnoreVersion(o)) {
      ArtifactDependencySpecImpl that = (ArtifactDependencySpecImpl)o;
      return Objects.equal(version, that.version);
    }
    return false;
  }

  @Override
  public int hashCode() {
    return Objects.hashCode(name, group, version, classifier, extension);
  }

  @Override
  public String toString() {
    return compactNotation();
  }

  @NotNull
  @Override
  public String compactNotation() {
    List<String> segments = Lists.newArrayList(group, name, version, classifier);
    String s = Joiner.on(GRADLE_PATH_SEPARATOR).skipNulls().join(segments);
    if (extension != null) {
      s += "@" + extension;
    }
    return s;
  }
}
