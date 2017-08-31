/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package com.intellij.lang.ant.config.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2009
 */
public class AntBeforeRunTask extends BeforeRunTask<AntBeforeRunTask>{
  private String myTargetName;
  private String myAntFileUrl;

  public AntBeforeRunTask() {
    super(AntBeforeRunTaskProvider.ID);
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

  public void writeExternal(@NotNull Element element) {
    super.writeExternal(element);
    if (myAntFileUrl != null && myTargetName != null) {
      element.setAttribute("antfile", myAntFileUrl);
      element.setAttribute("target", myTargetName);
    }
  }

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
    return Comparing.equal(myTargetName, target.getName());
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
