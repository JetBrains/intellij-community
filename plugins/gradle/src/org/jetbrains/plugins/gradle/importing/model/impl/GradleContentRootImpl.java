package org.jetbrains.plugins.gradle.importing.model.impl;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.importing.model.GradleContentRoot;
import org.jetbrains.plugins.gradle.importing.model.GradleEntityVisitor;
import org.jetbrains.plugins.gradle.importing.model.SourceType;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/9/11 6:25 PM
 */
public class GradleContentRootImpl implements Serializable, GradleContentRoot {

  private static final long serialVersionUID = 1L;
  
  private final Map<SourceType, Collection<String>> myData  = new EnumMap<SourceType, Collection<String>>(SourceType.class);
  private final Map<SourceType, Collection<String>> myViews = new EnumMap<SourceType, Collection<String>>(SourceType.class);
  
  private final String myRootPath;

  /**
   * Creates new <code>GradleContentRootImpl</code> object.
   * 
   * @param rootPath  path to the root directory
   */
  public GradleContentRootImpl(@NotNull String rootPath) {
    myRootPath = new File(rootPath).getAbsolutePath();
    for (SourceType type : SourceType.values()) {
      Set<String> data = new HashSet<String>();
      myData.put(type, data);
      myViews.put(type, Collections.unmodifiableCollection(data));
    }
  }

  @NotNull
  @Override
  public Collection<String> getPaths(@NotNull SourceType type) {
    return myViews.get(type);
  }

  public void storePath(@NotNull SourceType type, @NotNull String path) {
    myData.get(type).add(new File(path).getAbsolutePath());
  }
  
  @NotNull
  @Override
  public String getRootPath() {
    return myRootPath;
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
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
}
