// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.util.config.Externalizer;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public final class TargetFilter implements JDOMExternalizable, Externalizer.SkippableValue {
  @NonNls private static final String FILTER_TARGET_NAME = "targetName";
  @NonNls private static final String FILTER_IS_VISIBLE = "isVisible";
  private @NlsSafe String myTargetName;
  private boolean myVisible;
  private @Nls String myDescription = "";

  public TargetFilter() {}

  public TargetFilter(@Nls String targetName, boolean isVisible) {
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

  @Override
  public void readExternal(Element element) {
    myTargetName = element.getAttributeValue(FILTER_TARGET_NAME);
    myVisible = Boolean.parseBoolean(element.getAttributeValue(FILTER_IS_VISIBLE));
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    final String targetName = getTargetName();
    if (targetName == null) {
      return;
    }
    element.setAttribute(FILTER_TARGET_NAME, targetName);
    element.setAttribute(FILTER_IS_VISIBLE, Boolean.valueOf(isVisible()).toString());
  }

  public String getDescription() {
    return myDescription;
  }

  public void updateDescription(AntBuildTarget target) {
    if (target == null) return;
    myDescription = target.getNotEmptyDescription();
  }

  @NotNull
  public static TargetFilter fromTarget(AntBuildTarget target) {
    TargetFilter filter = new TargetFilter(target.getName(), target.isDefault());
    filter.myDescription = target.getNotEmptyDescription();
    filter.myVisible = (filter.myDescription != null);
    return filter;
  }
}
