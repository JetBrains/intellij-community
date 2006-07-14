package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

public final class TargetFilter implements JDOMExternalizable {
  @NonNls private static final String FILTER_TARGET_NAME = "targetName";
  @NonNls private static final String FILTER_IS_VISIBLE = "isVisible";
  private String myTargetName;
  private boolean myVisible;
  private String myDescription = "";

  public TargetFilter() {}

  public TargetFilter(String targetName, boolean isVisible) {
    myTargetName = targetName;
    myVisible = isVisible;
  }

  public String getTargetName() {
    return myTargetName;
  }

  public boolean isVisible() {
    return myVisible;
  }

  public void setVisible(boolean isVisible) {
    myVisible = isVisible;
  }

  public void readExternal(Element element) {
    myTargetName = element.getAttributeValue(FILTER_TARGET_NAME);
    myVisible = Boolean.valueOf(element.getAttributeValue(FILTER_IS_VISIBLE));
  }

  public void writeExternal(Element element) {
    element.setAttribute(FILTER_TARGET_NAME, getTargetName());
    element.setAttribute(FILTER_IS_VISIBLE, Boolean.valueOf(isVisible()).toString());
  }

  public String getDescription() {
    return myDescription;
  }

  public void updateDescription(AntBuildTarget target) {
    if (target == null) return;
    myDescription = target.getNotEmptyDescription();
  }

  public static TargetFilter fromTarget(AntBuildTarget target) {
    TargetFilter filter = new TargetFilter(target.getName(), target.isDefault());
    filter.myDescription = target.getNotEmptyDescription();
    filter.myVisible = (filter.myDescription != null);
    return filter;
  }
}
