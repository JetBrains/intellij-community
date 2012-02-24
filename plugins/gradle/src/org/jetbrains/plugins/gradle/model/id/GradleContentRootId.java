package org.jetbrains.plugins.gradle.model.id;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.model.GradleEntityOwner;
import org.jetbrains.plugins.gradle.model.GradleEntityType;
import org.jetbrains.plugins.gradle.model.gradle.GradleContentRoot;
import org.jetbrains.plugins.gradle.model.gradle.GradleModule;
import org.jetbrains.plugins.gradle.util.GradleProjectStructureContext;

/**
 * @author Denis Zhdanov
 * @since 2/16/12 12:22 PM
 */
public class GradleContentRootId extends GradleAbstractEntityId {
  
  @NotNull private final String myModuleName;
  @NotNull private final String myRootPath;
  
  public GradleContentRootId(@NotNull GradleEntityOwner owner, @NotNull String moduleName, @NotNull String rootPath) {
    super(GradleEntityType.CONTENT_ROOT, owner);
    myModuleName = moduleName;
    myRootPath = rootPath;
  }

  @NotNull
  public String getRootPath() {
    return myRootPath;
  }

  @Override
  public Object mapToEntity(@NotNull GradleProjectStructureContext context) {
    switch (getOwner()) {
      case GRADLE:
        final GradleModule module = context.getProjectStructureHelper().findGradleModule(myModuleName);
        if (module == null) {
          return null;
        }
        for (GradleContentRoot root : module.getContentRoots()) {
          if (myRootPath.equals(root.getRootPath())) {
            return root;
          }
        }
        return null;
      case INTELLIJ: return null;
    }
    return null;
  }

  @Override
  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + myModuleName.hashCode();
    result = 31 * result + myRootPath.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (!super.equals(o)) return false;

    GradleContentRootId that = (GradleContentRootId)o;

    if (!myModuleName.equals(that.myModuleName)) return false;
    if (!myRootPath.equals(that.myRootPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    return "content root '" + myRootPath + "'";
  }
}
