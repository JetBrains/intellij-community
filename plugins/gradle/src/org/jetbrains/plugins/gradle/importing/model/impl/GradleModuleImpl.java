package org.jetbrains.plugins.gradle.importing.model.impl;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.importing.model.*;

import java.io.File;
import java.io.Serializable;
import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:11 PM
 */
public class GradleModuleImpl implements GradleModule, Serializable {

  private static final long serialVersionUID = 1L;

  private final List<GradleContentRoot>      myContentRoots       = new ArrayList<GradleContentRoot>();
  private final Map<SourceType, String>      myCompileOutputPaths = new HashMap<SourceType, String>();
  private final Set<GradleDependency>        myDependencies       = new HashSet<GradleDependency>();
  private final Collection<GradleDependency> myDependenciesView   = Collections.unmodifiableCollection(myDependencies);
  
  private boolean myInheritProjectCompileOutputPath = true;
  
  private String myName;

  public GradleModuleImpl(@NotNull String name) {
    myName = name;
  }

  @NotNull
  @Override
  public String getName() {
    return myName;
  }

  @Override
  public void setName(@NotNull String name) {
    myName = name; 
  }

  @NotNull
  @Override
  public Collection<GradleContentRoot> getContentRoots() {
    return myContentRoots;
  }

  public void addContentRoot(@NotNull GradleContentRoot contentRoot) {
    myContentRoots.add(contentRoot);
  }

  @Override
  public boolean isInheritProjectCompileOutputPath() {
    return myInheritProjectCompileOutputPath;
  }

  public void setInheritProjectCompileOutputPath(boolean inheritProjectCompileOutputPath) {
    myInheritProjectCompileOutputPath = inheritProjectCompileOutputPath;
  }

  @Nullable
  @Override
  public String getCompileOutputPath(@NotNull SourceType type) {
    return myCompileOutputPaths.get(type);
  }

  public void setCompileOutputPath(@NotNull SourceType type, @Nullable String path) {
    if (path == null) {
      myCompileOutputPaths.remove(type);
      return;
    }
    myCompileOutputPaths.put(type, new File(path).getAbsolutePath());
  }

  @NotNull
  @Override
  public Collection<GradleDependency> getDependencies() {
    return myDependenciesView;
  }

  public void addDependency(@NotNull GradleDependency dependency) {
    myDependencies.add(dependency);
  }

  @Override
  public void invite(@NotNull GradleEntityVisitor visitor) {
    visitor.visit(this);
  }

  @Override
  public int hashCode() {
    int result = myContentRoots.hashCode();
    result = 31 * result + myCompileOutputPaths.hashCode();
    result = 31 * result + myDependencies.hashCode();
    result = 31 * result + myDependenciesView.hashCode();
    result = 31 * result + (myInheritProjectCompileOutputPath ? 1 : 0);
    result = 31 * result + myName.hashCode();
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleModuleImpl that = (GradleModuleImpl)o;

    if (myInheritProjectCompileOutputPath != that.myInheritProjectCompileOutputPath) return false;
    if (!myCompileOutputPaths.equals(that.myCompileOutputPaths)) return false;
    if (!myContentRoots.equals(that.myContentRoots)) return false;
    if (!myDependencies.equals(that.myDependencies)) return false;
    if (!myDependenciesView.equals(that.myDependenciesView)) return false;
    if (!myName.equals(that.myName)) return false;

    return true;
  }

  @Override
  public String toString() {
    return String.format(
      "module '%s'. Content roots: %s; inherit compile output path: %b",
      getName(), getContentRoots(), isInheritProjectCompileOutputPath()
    );
  }
}
