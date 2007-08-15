package com.intellij.execution.junit2.info;

import com.intellij.execution.Location;
import com.intellij.execution.junit2.segments.ObjectReader;
import com.intellij.openapi.project.Project;

public abstract class ClassBasedInfo extends TestInfoImpl {
  private final DisplayTestInfoExtractor myClassInfo;
  private PsiClassLocator myClass;
  private String myComment = null;

  public ClassBasedInfo(final DisplayTestInfoExtractor classInfo) {
    myClassInfo = classInfo;
  }

  protected void readClass(final ObjectReader reader) {
    setClassName(reader.readLimitedString());
  }

  protected void setClassName(final String name) {
    myClass = PsiClassLocator.fromQualifiedName(name);
    myComment = null;
  }

  public Location getLocation(final Project project) {
    return myClass.getLocation(project);
  }

  public String getComment() {
    if (myComment == null) {
      myComment = myClassInfo.getComment(myClass);
    }
    return myComment;
  }

  public String getName() {
    return myClassInfo.getName(myClass);
  }
}
