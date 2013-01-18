package org.jetbrains.plugins.gradle.model.id;

import com.intellij.openapi.roots.OrderRootType;
import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.gradle.GradleJar;
import org.jetbrains.plugins.gradle.model.gradle.GradleLibrary;
import org.jetbrains.plugins.gradle.model.gradle.LibraryPathType;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 3:04 PM
 */
public class GradleJarId extends GradleAbstractEntityId {

  @NotNull private final String          myPath;
  @NotNull private final GradleLibraryId myLibraryId;
  @NotNull private final LibraryPathType myPathType;

  public GradleJarId(@NotNull String path, @NotNull LibraryPathType pathType, @NotNull GradleLibraryId libraryId) {
    super(GradleEntityType.JAR, libraryId.getOwner());
    myPath = path;
    myPathType = pathType;
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

  @NotNull
  public LibraryPathType getLibraryPathType() {
    return myPathType;
  }

  @Nullable
  @Override
  public GradleJar mapToEntity(@NotNull GradleProjectStructureContext context) {
    String libraryName = myLibraryId.getLibraryName();
    OrderRootType jarType = context.getLibraryPathTypeMapper().map(myPathType);
    Library intellijLibrary = context.getProjectStructureHelper().findIdeLibrary(libraryName, jarType, myPath);
    if (intellijLibrary != null) {
      return new GradleJar(myPath, myPathType, intellijLibrary, null);
    }

    GradleLibrary gradleLibrary = context.getProjectStructureHelper().findGradleLibrary(libraryName, myPathType, myPath);
    if (gradleLibrary != null) {
      return new GradleJar(myPath, myPathType, null, gradleLibrary);
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
    return String.format("%s jar '%s'", myPathType.toString().toLowerCase(), myPath);
  }
}
