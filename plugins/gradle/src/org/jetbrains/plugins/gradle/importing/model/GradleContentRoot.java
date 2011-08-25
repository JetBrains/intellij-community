package org.jetbrains.plugins.gradle.importing.model;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/9/11 6:25 PM
 */
public class GradleContentRoot extends AbstractGradleEntity {

  private static final long serialVersionUID = 1L;
  
  private final Map<SourceType, Collection<String>> myData  = new EnumMap<SourceType, Collection<String>>(SourceType.class);
  
  private final String myRootPath;

  /**
   * Creates new <code>GradleContentRootImpl</code> object.
   * 
   * @param rootPath  path to the root directory
   */
  public GradleContentRoot(@NotNull String rootPath) {
    myRootPath = GradleUtil.toCanonicalPath(rootPath);
    for (SourceType type : SourceType.values()) {
      Set<String> data = new HashSet<String>();
      myData.put(type, data);
    }
  }

  /**
   * @param type      target dir type
   * @return          directories of the target type configured for the current content root
   */
  @NotNull
  public Collection<String> getPaths(@NotNull SourceType type) {
    return myData.get(type);
  }

  public void storePath(@NotNull SourceType type, @NotNull String path) {
    myData.get(type).add(GradleUtil.toCanonicalPath(path));
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
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleContentRoot that = (GradleContentRoot)o;

    if (!myData.equals(that.myData)) return false;
    if (!myRootPath.equals(that.myRootPath)) return false;

    return true;
  }

  @Override
  public String toString() {
    StringBuilder buffer = new StringBuilder();
    for (Map.Entry<SourceType, Collection<String>> entry : myData.entrySet()) {
      buffer.append(entry.getKey().toString().toLowerCase()).append(": ").append(entry.getValue()).append("; ");
    }
    buffer.setLength(buffer.length() - 2);
    return buffer.toString();
  }

  @Override
  public GradleContentRoot clone() {
    GradleContentRoot result = new GradleContentRoot(getRootPath());
    for (Map.Entry<SourceType, Collection<String>> entry : myData.entrySet()) {
      for (String path : entry.getValue()) {
        result.storePath(entry.getKey(), path);
      }
    }
    return result;
  }
}
