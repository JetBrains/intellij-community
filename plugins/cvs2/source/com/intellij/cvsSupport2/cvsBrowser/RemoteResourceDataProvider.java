package com.intellij.cvsSupport2.cvsBrowser;

import com.intellij.openapi.project.Project;

public interface RemoteResourceDataProvider {
  RemoteResourceDataProvider NOT_EXPANDABLE = new RemoteResourceDataProvider(){
    public void fillContentFor(CvsElement element, Project project, GetContentCallback callback) {

    }

  };

  void fillContentFor(CvsElement element, Project project, GetContentCallback callback);
}
