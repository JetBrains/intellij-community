package org.jetbrains.android.maven;

import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Tag;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
* @author Eugene.Kudelevsky
*/
@Tag("resolved-info")
public class MavenArtifactResolvedInfo {
  private String myApiLevel;
  private List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo>
    myApklibDependencies = new ArrayList<AndroidExternalApklibDependenciesManager.MavenDependencyInfo>();

  public MavenArtifactResolvedInfo(String apiLevel,
                                   Collection<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> dependencyInfos) {
    myApiLevel = apiLevel;
    myApklibDependencies = new ArrayList<AndroidExternalApklibDependenciesManager.MavenDependencyInfo>(dependencyInfos);
  }

  public MavenArtifactResolvedInfo() {
  }

  public String getApiLevel() {
    return myApiLevel;
  }

  @Tag("dependencies")
  @AbstractCollection(surroundWithTag = false)
  public List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> getApklibDependencies() {
    return myApklibDependencies;
  }

  public void setApklibDependencies(List<AndroidExternalApklibDependenciesManager.MavenDependencyInfo> apklibDependencies) {
    myApklibDependencies = apklibDependencies;
  }

  public void setApiLevel(String apiLevel) {
    myApiLevel = apiLevel;
  }
}
