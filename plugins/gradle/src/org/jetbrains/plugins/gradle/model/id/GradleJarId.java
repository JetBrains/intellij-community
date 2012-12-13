package org.jetbrains.plugins.gradle.model.id;

import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.gradle.GradleJar;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 3:04 PM
 */
public class GradleJarId extends GradleAbstractEntityId {
  
  @NotNull private final String myPath;
  @NotNull private final GradleLibraryId myLibraryId;
  
  public GradleJarId(@NotNull String path, @NotNull GradleLibraryId libraryId) {
    super(GradleEntityType.JAR, libraryId.getOwner());
    myPath = path;
    myLibraryId = libraryId;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public GradleLibraryId getLibraryId() {
    return myLibraryId;
  }

  @Nullable
  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    Library intellijLibrary = context.getProjectStructureHelper().findIntellijLibrary(myLibraryId.getLibraryName(), myPath);
    if (intellijLibrary != null) {
      return new GradleJar(myPath, intellijLibrary, null);
    }

    GradleLibrary gradleLibrary = context.getProjectStructureHelper().findGradleLibrary(myLibraryId.getLibraryName(), myPath);
    if (gradleLibrary != null) {
      return new GradleJar(myPath, null, gradleLibrary);
    }
    return null;
  }
  
  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    GradleJarId that = (GradleJarId)o;
    return myPath.equals(that.myPath);
  }

  @Override
  public String toString() {
    return String.format("jar '%s'", myPath);
  }
}
