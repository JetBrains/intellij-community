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
package org.jetbrains.plugins.gradle.diff.library;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.diff.AbstractGradleProjectStructureChange;
import org.jetbrains.plugins.gradle.diff.GradleProjectStructureChangeVisitor;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId;

/**
 * There is a possible case that particular library has different versions at gradle and ide sides. Object of this class
 * encapsulates that information.
 * 
 * @author Denis Zhdanov
 * @since 1/22/13 12:21 PM
 */
public class GradleOutdatedLibraryVersionChange extends AbstractGradleProjectStructureChange {

  @NotNull private final String          myBaseLibraryName;
  @NotNull private final GradleLibraryId myGradleLibraryId;
  @NotNull private final String          myGradleLibraryVersion;
  @NotNull private final GradleLibraryId myIdeLibraryId;
  @NotNull private final String          myIdeLibraryVersion;

  public GradleOutdatedLibraryVersionChange(@NotNull String baseLibraryName,
                                            @NotNull GradleLibraryId gradleLibraryId,
                                            @NotNull String gradleLibraryVersion,
                                            @NotNull GradleLibraryId ideLibraryId,
                                            @NotNull String ideLibraryVersion)
  {
    myBaseLibraryName = baseLibraryName;
    myGradleLibraryId = gradleLibraryId;
    myGradleLibraryVersion = gradleLibraryVersion;
    myIdeLibraryId = ideLibraryId;
    myIdeLibraryVersion = ideLibraryVersion;
  }

  @NotNull
  public String getBaseLibraryName() {
    return myBaseLibraryName;
  }

  @NotNull
  public GradleLibraryId getGradleLibraryId() {
    return myGradleLibraryId;
  }

  @NotNull
  public String getGradleLibraryVersion() {
    return myGradleLibraryVersion;
  }

  @NotNull
  public GradleLibraryId getIdeLibraryId() {
    return myIdeLibraryId;
  }

  @NotNull
  public String getIdeLibraryVersion() {
    return myIdeLibraryVersion;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myBaseLibraryName.hashCode();
    result = 31 * result + myGradleLibraryId.hashCode();
    result = 31 * result + myGradleLibraryVersion.hashCode();
    result = 31 * result + myIdeLibraryId.hashCode();
    result = 31 * result + myIdeLibraryVersion.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    GradleOutdatedLibraryVersionChange change = (GradleOutdatedLibraryVersionChange)o;

    if (!myBaseLibraryName.equals(change.myBaseLibraryName)) return false;
    if (!myGradleLibraryId.equals(change.myGradleLibraryId)) return false;
    if (!myGradleLibraryVersion.equals(change.myGradleLibraryVersion)) return false;
    if (!myIdeLibraryId.equals(change.myIdeLibraryId)) return false;
    if (!myIdeLibraryVersion.equals(change.myIdeLibraryVersion)) return false;

    return true;
  }

  @Override
  public void invite(@NotNull GradleProjectStructureChangeVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public String toString() {
    return String.format("'%s' library version change: '%s' -> '%s'", myBaseLibraryName, myIdeLibraryVersion, myGradleLibraryVersion);
  }
}