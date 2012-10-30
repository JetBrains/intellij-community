/*
 * Copyright 2000-2012 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.android.maven;

import com.intellij.openapi.components.*;
import com.intellij.openapi.components.StoragePathMacros;
import com.intellij.openapi.project.Project;
import com.intellij.util.containers.HashMap;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.MapAnnotation;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.maven.model.MavenId;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Eugene.Kudelevsky
 */
@State(
  name = "AndroidExternalApklibDependenciesManager",
  storages = {
    @Storage(file = StoragePathMacros.WORKSPACE_FILE)
  }
)
public class AndroidExternalApklibDependenciesManager implements PersistentStateComponent<AndroidExternalApklibDependenciesManager.State> {
  private State myState = new State();

  public static AndroidExternalApklibDependenciesManager getInstance(@NotNull Project project) {
    return ServiceManager.getService(project, AndroidExternalApklibDependenciesManager.class);
  }

  @Override
  public AndroidExternalApklibDependenciesManager.State getState() {
    return myState;
  }

  @Override
  public void loadState(AndroidExternalApklibDependenciesManager.State state) {
    myState = state;
  }

  @Nullable
  public MyResolvedInfo getResolvedInfoForArtifact(@NotNull MavenId mavenId) {
    final String key = AndroidMavenUtil.getMavenIdStringForFileName(mavenId);
    return myState.getResolvedInfoMap().get(key);
  }

  @Nullable
  public String getArtifactFilePath(@NotNull String mavenIdStr) {
    return myState.getArtifactFilesMap().get(mavenIdStr);
  }

  public void setSdkInfoForArtifact(@NotNull MavenId mavenId, @NotNull MyResolvedInfo info) {
    final String key = AndroidMavenUtil.getMavenIdStringForFileName(mavenId);
    myState.getResolvedInfoMap().put(key, info);
  }

  public void setArtifactFilePath(@NotNull MavenId mavenId, @NotNull String path) {
    final String key = AndroidMavenUtil.getMavenIdStringForFileName(mavenId);
    myState.getArtifactFilesMap().put(key, path);
  }

  public static class State {
    private Map<String, String> myArtifactFilesMap = new HashMap<String, String>();
    private Map<String, MyResolvedInfo> myResolvedInfoMap = new HashMap<String, MyResolvedInfo>();

    @Tag("sdk-infos")
    @MapAnnotation(surroundWithTag = false)
    public Map<String, MyResolvedInfo> getResolvedInfoMap() {
      return myResolvedInfoMap;
    }

    @Tag("artifacts")
    @MapAnnotation(surroundWithTag = false)
    public Map<String, String> getArtifactFilesMap() {
      return myArtifactFilesMap;
    }

    public void setArtifactFilesMap(Map<String, String> artifactFilesMap) {
      myArtifactFilesMap = artifactFilesMap;
    }

    public void setResolvedInfoMap(Map<String, MyResolvedInfo> artifactId2SdkData) {
      myResolvedInfoMap = artifactId2SdkData;
    }
  }

  @Tag("resolved-info")
  public static class MyResolvedInfo {
    private String myApiLevel;
    private String mySdkPath;
    private List<MavenDependencyInfo> myApklibDependencies = new ArrayList<MavenDependencyInfo>();

    public MyResolvedInfo(String apiLevel, String sdkPath, Collection<MavenDependencyInfo> dependencyInfos) {
      myApiLevel = apiLevel;
      mySdkPath = sdkPath;

      myApklibDependencies = new ArrayList<MavenDependencyInfo>(dependencyInfos);
    }

    public MyResolvedInfo() {
    }

    public String getApiLevel() {
      return myApiLevel;
    }

    public String getSdkPath() {
      return mySdkPath;
    }

    @Tag("dependencies")
    @AbstractCollection(surroundWithTag = false)
    public List<MavenDependencyInfo> getApklibDependencies() {
      return myApklibDependencies;
    }

    public void setApklibDependencies(List<MavenDependencyInfo> apklibDependencies) {
      myApklibDependencies = apklibDependencies;
    }

    public void setApiLevel(String apiLevel) {
      myApiLevel = apiLevel;
    }

    public void setSdkPath(String sdkPath) {
      mySdkPath = sdkPath;
    }
  }
  
  public static class MavenDependencyInfo {
    private String myGroupId;
    private String myArtifactId;
    private String myVersion;
    private String myType;
    private String myScope;

    public MavenDependencyInfo() {
    }

    public MavenDependencyInfo(@NotNull MavenId mavenId, @NotNull String type, @NotNull String scope) {
      myGroupId = mavenId.getGroupId();
      myArtifactId = mavenId.getArtifactId();
      myVersion = mavenId.getVersion();
      myType = type;
      myScope = scope;
    }

    public String getGroupId() {
      return myGroupId;
    }

    public String getArtifactId() {
      return myArtifactId;
    }

    public String getVersion() {
      return myVersion;
    }

    public String getType() {
      return myType;
    }

    public String getScope() {
      return myScope;
    }

    public void setGroupId(String groupId) {
      myGroupId = groupId;
    }

    public void setArtifactId(String artifactId) {
      myArtifactId = artifactId;
    }

    public void setVersion(String version) {
      myVersion = version;
    }

    public void setType(String type) {
      myType = type;
    }

    public void setScope(String scope) {
      myScope = scope;
    }
  }
}
