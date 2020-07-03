// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

import java.util.Objects;

/**
 * @author Eugene Zhuravlev
 */
public final class AntBeforeRunTask extends BeforeRunTask<AntBeforeRunTask>{
  private final Project project;
  private String myTargetName;
  private String myAntFileUrl;

  public AntBeforeRunTask(@NotNull Project project) {
    super(AntBeforeRunTaskProvider.ID);

    this.project = project;
  }

  public @NotNull Project getProject() {
    return project;
  }

  public String getAntFileUrl() {
    return myAntFileUrl;
  }

  public void setAntFileUrl(String url) {
    myAntFileUrl = url;
  }

  public String getTargetName() {
    return myTargetName;
  }

  public void setTargetName(String targetName) {
    myTargetName = targetName;
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    if (myAntFileUrl != null && myTargetName != null) {
      element.setAttribute("antfile", myAntFileUrl);
      element.setAttribute("target", myTargetName);
    }
  }

  @Override
  public void readExternal(@NotNull Element element) {
    super.readExternal(element);
    final String url = element.getAttributeValue("antfile");
    final String targetName = element.getAttributeValue("target");
    if (url != null && targetName != null) {
      myAntFileUrl = url;
      myTargetName = targetName;
    }
  }

  public boolean isRunningTarget(AntBuildTarget target) {
    final VirtualFile vFile = target.getModel().getBuildFile().getVirtualFile();
    if (vFile == null) {
      return false;
    }
    if (myAntFileUrl == null || !FileUtil.pathsEqual(myAntFileUrl, vFile.getUrl())) {
      return false;
    }
    return Objects.equals(myTargetName, target.getName());
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    if (!super.equals(o)) return false;

    AntBeforeRunTask that = (AntBeforeRunTask)o;

    if (myAntFileUrl != null ? !myAntFileUrl.equals(that.myAntFileUrl) : that.myAntFileUrl != null) return false;
    if (myTargetName != null ? !myTargetName.equals(that.myTargetName) : that.myTargetName != null) return false;

    return true;
  }

  public int hashCode() {
    int result = super.hashCode();
    result = 31 * result + (myTargetName != null ? myTargetName.hashCode() : 0);
    result = 31 * result + (myAntFileUrl != null ? myAntFileUrl.hashCode() : 0);
    return result;
  }
}
