package org.jetbrains.plugins.gradle.model.gradle;

import com.intellij.openapi.util.io.FileUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.io.File;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/9/11 6:25 PM
 */
public class GradleContentRoot extends AbstractGradleEntity {

  private static final long serialVersionUID = 1L;
  
  private final Map<SourceType, Collection<String>> myData  = new EnumMap<SourceType, Collection<String>>(SourceType.class);

  private final GradleModule myOwnerModule;
  private final String       myRootPath;

  /**
   * Creates new <code>GradleContentRootImpl</code> object.
   * 
   * @param rootPath  path to the root directory
   */
  public GradleContentRoot(@NotNull GradleModule ownerModule, @NotNull String rootPath) {
    myOwnerModule = ownerModule;
    myRootPath = GradleUtil.toCanonicalPath(rootPath);
    for (SourceType type : SourceType.values()) {
      Set<String> data = new HashSet<String>();
      myData.put(type, data);
    }
  }

  @NotNull
  public GradleModule getOwnerModule() {
    return myOwnerModule;
  }

  /**
   * @param type      target dir type
   * @return          directories of the target type configured for the current content root
   */
  @NotNull
  public Collection<String> getPaths(@NotNull SourceType type) {
    return myData.get(type);
  }

  /**
   * Ask to remember that directory at the given path contains sources of the given type.
   * 
   * @param type  target sources type
   * @param path  target source directory path
   * @throws IllegalArgumentException   if given path points to the directory that is not located
   *                                    under the {@link #getRootPath() content root}
   */
  public void storePath(@NotNull SourceType type, @NotNull String path) throws IllegalArgumentException {
    if (FileUtil.isAncestor(new File(getRootPath()), new File(path), false)) {
      myData.get(type).add(GradleUtil.toCanonicalPath(path));
      return;
    }
    if (type != SourceType.EXCLUDED) { // Sometimes gradle marks output directory as 'excluded' path. We don't need to bother
                                       // if it's outside a module content root then.
      throw new IllegalArgumentException(String.format(
        "Can't register given path of type '%s' because it's out of content root.%nContent root: '%s'%nGiven path: '%s'",
        type, getRootPath(), new File(path).getAbsolutePath()
      ));
    }
  }
  
  @NotNull
  public String getRootPath() {
    return myRootPath;
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myData.hashCode();
    result = 31 * result + myRootPath.hashCode();
    return 31 * result + myOwnerModule.getName().hashCode();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleContentRoot that = (GradleContentRoot)o;

    if (!myOwnerModule.getName().equals(that.myOwnerModule.getName())) return false;
    if (!myData.equals(that.myData)) return false;
    if (!myRootPath.equals(that.myRootPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder("content root:");
    for (Map.Entry<SourceType, Collection<String>> entry : myData.entrySet()) {
      buffer.append(entry.getKey().toString().toLowerCase()).append("=").append(entry.getValue()).append("|");
    }
    if (!myData.isEmpty()) {
      buffer.setLength(buffer.length() - 1);
    }
    return buffer.toString();
  }

  @NotNull
  @Override
  public GradleContentRoot clone(@NotNull GradleEntityCloneContext context) {
    GradleContentRoot result = new GradleContentRoot(getOwnerModule(), getRootPath());
    for (Map.Entry<SourceType, Collection<String>> entry : myData.entrySet()) {
      for (String path : entry.getValue()) {
        result.storePath(entry.getKey(), path);
      }
    }
    return result;
  }
}
