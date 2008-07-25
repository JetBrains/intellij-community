package com.intellij.lang.ant.config;

import org.jdom.Element;
import com.intellij.openapi.project.Project;

public abstract class ExecutionEvent {
  public abstract String getTypeId();

  public abstract String getPresentableName();

  public void readExternal(Element element, Project project) {
  }

  public void writeExternal(Element element, Project project) {
  }
}
