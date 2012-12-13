package org.jetbrains.plugins.gradle.model.gradle;

import com.intellij.openapi.roots.libraries.Library;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.id.GradleJarId;
import org.jetbrains.plugins.gradle.model.id.GradleLibraryId;
import org.jetbrains.plugins.gradle.util.GradleUtil;

/**
 * @author Denis Zhdanov
 * @since 12/11/12 3:07 PM
 */
public class GradleJar extends AbstractNamedGradleEntity {
  
  @NotNull private final String myPath;
  
  @Nullable private final Library myIntellijLibrary;
  @Nullable private final GradleLibrary myGradleLibrary;

  public GradleJar(@NotNull String path, @Nullable Library intellijLibrary, @Nullable GradleLibrary gradleLibrary) {
    super(GradleUtil.extractNameFromPath(path));
    assert intellijLibrary == null ^ gradleLibrary == null;
    myPath = path;
    myIntellijLibrary = intellijLibrary;
    myGradleLibrary = gradleLibrary;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public GradleJarId getId() {
    return new GradleJarId(myPath, getLibraryId());
  }
  
  @NotNull
  public GradleLibraryId getLibraryId() {
    if (myIntellijLibrary != null) {
      return new GradleLibraryId(GradleEntityOwner.INTELLIJ, GradleUtil.getLibraryName(myIntellijLibrary));
    }
    assert myGradleLibrary != null;
    return new GradleLibraryId(GradleEntityOwner.GRADLE, myGradleLibrary.getName());
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @NotNull
  @Override
  public GradleJar clone(@NotNull GradleEntityCloneContext context) {
    return new GradleJar(myPath, myIntellijLibrary, myGradleLibrary == null ? null : context.getLibrary(myGradleLibrary));
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    result = 31 * result + (myIntellijLibrary != null ? myIntellijLibrary.hashCode() : 0);
    result = 31 * result + (myGradleLibrary != null ? myGradleLibrary.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    GradleJar that = (GradleJar)o;
    
    if (!myPath.equals(that.myPath)) return false;
    if (myGradleLibrary != null ? !myGradleLibrary.equals(that.myGradleLibrary) : that.myGradleLibrary != null) return false;
    if (myIntellijLibrary == null && that.myIntellijLibrary != null) {
      return false;
    }
    else if (myIntellijLibrary != null) {
      if (that.myIntellijLibrary == null) {
        return false;
      }
      else if (!GradleUtil.getLibraryName(myIntellijLibrary).equals(GradleUtil.getLibraryName(that.myIntellijLibrary))) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public String toString() {
    return String.format(
      "jar at '%s'. Belongs to library '%s'",
      myPath, myIntellijLibrary == null ? myGradleLibrary.getName() : GradleUtil.getLibraryName(myIntellijLibrary)
    );
  }
}
