package org.jetbrains.plugins.gradle.importing.model;

import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.util.GradleUtil;

import java.util.*;

/**
 * @author Denis Zhdanov
 * @since 8/8/11 12:11 PM
 */
public class GradleModule extends AbstractNamedGradleEntity implements Named {

  private static final long serialVersionUID = 1L;

  private final List<GradleContentRoot> myContentRoots       = new ArrayList<GradleContentRoot>();
  private final Map<SourceType, String> myCompileOutputPaths = new HashMap<SourceType, String>();
  private final Set<GradleDependency>   myDependencies       = new HashSet<GradleDependency>();
  
  private boolean myInheritProjectCompileOutputPath = true;
  
  public GradleModule(@NotNull String name) {
    super(name);
  }

  @NotNull
  public Collection<GradleContentRoot> getContentRoots() {
    return myContentRoots;
  }

  public void addContentRoot(@NotNull GradleContentRoot contentRoot) {
    myContentRoots.add(contentRoot);
  }

  public boolean isInheritProjectCompileOutputPath() {
    return myInheritProjectCompileOutputPath;
  }

  public void setInheritProjectCompileOutputPath(boolean inheritProjectCompileOutputPath) {
    myInheritProjectCompileOutputPath = inheritProjectCompileOutputPath;
  }

  /**
   * Allows to get file system path of the compile output of the source of the target type.
   *
   * @param type  target source type
   * @return      file system path to use for compile output for the target source type;
   *              {@link GradleProject#getCompileOutputPath() project compile output path} should be used if current module
   *              doesn't provide specific compile output path
   */
  @Nullable
  public String getCompileOutputPath(@NotNull SourceType type) {
    return myCompileOutputPaths.get(type);
  }

  public void setCompileOutputPath(@NotNull SourceType type, @Nullable String path) {
    if (path == null) {
      myCompileOutputPaths.remove(type);
      return;
    }
    myCompileOutputPaths.put(type, GradleUtil.toCanonicalPath(path));
  }

  @NotNull
  public Collection<GradleDependency> getDependencies() {
    return myDependencies;
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
    result = 31 * result + myDependencies.hashCode();
    result = 31 * result + (myInheritProjectCompileOutputPath ? 1 : 0);
    result = 31 * result + super.hashCode();
    
    // We intentionally don't use dependencies here in order to allow module mappings before and after external libraries
    // resolving (downloading)
    
    return result;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    GradleModule that = (GradleModule)o;

    if (!super.equals(that)) return false;
    if (myInheritProjectCompileOutputPath != that.myInheritProjectCompileOutputPath) return false;
    if (!myCompileOutputPaths.equals(that.myCompileOutputPaths)) return false;
    if (!myContentRoots.equals(that.myContentRoots)) return false;
    
    // We intentionally don't use dependencies here in order to allow module mappings before and after external libraries
    // resolving (downloading)
    
    return true;
  }

  @Override
  public String toString() {
    return String.format(
      "module '%s'. Content roots: %s; inherit compile output path: %b",
      getName(), getContentRoots(), isInheritProjectCompileOutputPath()
    );
  }

  @Override
  public GradleModule clone() {
    GradleModule result = new GradleModule(getName());
    for (GradleContentRoot contentRoot : getContentRoots()) {
      result.addContentRoot(contentRoot.clone());
    }
    for (Map.Entry<SourceType, String> entry : myCompileOutputPaths.entrySet()) {
      result.setCompileOutputPath(entry.getKey(), entry.getValue());
    }
    return result;
  }
}
