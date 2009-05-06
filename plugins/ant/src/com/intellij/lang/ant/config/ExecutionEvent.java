package com.intellij.lang.ant.config;

import com.intellij.openapi.project.Project;
import org.jdom.Element;

public abstract class ExecutionEvent {
  public abstract String getTypeId();

  public abstract String getPresentableName();

  public void readExternal(Element element, Project project) {
  }

  public String writeExternal(Element element, Project project) {
    return getTypeId();
  }
}
