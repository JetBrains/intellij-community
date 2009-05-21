package com.intellij.lang.ant.config.impl;

import com.intellij.execution.BeforeRunTask;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vfs.VirtualFile;
import org.jdom.Element;

/**
 * @author Eugene Zhuravlev
 *         Date: May 18, 2009
 */
public class AntBeforeRunTask extends BeforeRunTask{
  private String myTargetName;
  private String myAntFileUrl;

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

  public void writeExternal(Element element) {
    super.writeExternal(element);
    if (myAntFileUrl != null && myTargetName != null) {
      element.setAttribute("antfile", myAntFileUrl);
      element.setAttribute("target", myTargetName);
    }
  }

  public void readExternal(Element element) {
    super.readExternal(element);
    final String url = element.getAttributeValue("antfile");
    final String targetName = element.getAttributeValue("target");
    if (url != null && targetName != null) {
      myAntFileUrl = url;
      myTargetName = targetName;
    }
  }

  public boolean isRunningTarget(AntBuildTarget target) {
    if (!isEnabled()) {
      return false;
    }
    final VirtualFile vFile = target.getModel().getBuildFile().getVirtualFile();
    if (vFile == null) {
      return false;
    }
    if (myAntFileUrl == null || !FileUtil.pathsEqual(myAntFileUrl, vFile.getUrl())) {
      return false;
    }
    return Comparing.equal(myTargetName, target.getName());
  }
}
