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

  @Nullable private final Library         myIdeLibrary;
  @Nullable private final GradleLibrary   myGradleLibrary;
  @NotNull private final  LibraryPathType myPathType;

  public GradleJar(@NotNull String path,
                   @NotNull LibraryPathType pathType,
                   @Nullable Library ideLibrary,
                   @Nullable GradleLibrary gradleLibrary)
  {
    super(GradleUtil.extractNameFromPath(path));
    myPathType = pathType;
    assert ideLibrary == null ^ gradleLibrary == null;
    myPath = path;
    myIdeLibrary = ideLibrary;
    myGradleLibrary = gradleLibrary;
  }

  @NotNull
  public String getPath() {
    return myPath;
  }

  @NotNull
  public LibraryPathType getPathType() {
    return myPathType;
  }

  @NotNull
  public GradleJarId getId() {
    return new GradleJarId(myPath, myPathType, getLibraryId());
  }
  
  @NotNull
  public GradleLibraryId getLibraryId() {
    if (myIdeLibrary != null) {
      return new GradleLibraryId(GradleEntityOwner.IDE, GradleUtil.getLibraryName(myIdeLibrary));
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
    return new GradleJar(myPath, myPathType, myIdeLibrary, myGradleLibrary == null ? null : context.getLibrary(myGradleLibrary));
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myPath.hashCode();
    result = 31 * result + myPathType.hashCode();
    result = 31 * result + (myIdeLibrary != null ? myIdeLibrary.hashCode() : 0);
    result = 31 * result + (myGradleLibrary != null ? myGradleLibrary.hashCode() : 0);
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    GradleJar that = (GradleJar)o;
    
    if (!myPath.equals(that.myPath)) return false;
    if (!myPathType.equals(that.myPathType)) return false;
    if (myGradleLibrary != null ? !myGradleLibrary.equals(that.myGradleLibrary) : that.myGradleLibrary != null) return false;
    if (myIdeLibrary == null && that.myIdeLibrary != null) {
      return false;
    }
    else if (myIdeLibrary != null) {
      if (that.myIdeLibrary == null) {
        return false;
      }
      else if (!GradleUtil.getLibraryName(myIdeLibrary).equals(GradleUtil.getLibraryName(that.myIdeLibrary))) {
        return false;
      }
    }
    return true;
  }

  @SuppressWarnings("ConstantConditions")
  @Override
  public String toString() {
    return String.format(
      "%s jar at '%s'. Belongs to library '%s'",
      myPathType.toString().toLowerCase(), myPath,
      myIdeLibrary == null ? myGradleLibrary.getName() : GradleUtil.getLibraryName(myIdeLibrary)
    );
  }
}
