// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.ExecutionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public final class ExecuteCompositeTargetEvent extends ExecutionEvent {
  public static final @NonNls String TYPE_ID = "compositeTask";
  private final @Nls String myCompositeName;
  private @Nls String myPresentableName;
  private final List<@NlsSafe String> myTargetNames;
  public static final @NonNls String PRESENTABLE_NAME = "presentableName";


  public ExecuteCompositeTargetEvent(final @NlsSafe String compositeName) throws WrongNameFormatException {
    if (!(compositeName.startsWith("[") && compositeName.endsWith("]") && compositeName.length() > 2)) {
      throw new WrongNameFormatException(compositeName);
    }
    myCompositeName = compositeName;
    final StringTokenizer tokenizer = new StringTokenizer(compositeName.substring(1, compositeName.length() - 1), ",", false);
    final List<String> targetNames = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      targetNames.add(tokenizer.nextToken().trim());
    }
    myTargetNames = targetNames;
    myPresentableName = compositeName;
  }

  public ExecuteCompositeTargetEvent(List<@Nls String> targetNames) {
    myTargetNames = targetNames;
    final @NlsSafe StringBuilder builder = new StringBuilder();
    boolean first = true;
    builder.append("[");
    for (String name : targetNames) {
      if (first) {
        first = false;
      }
      else {
        builder.append(",");
      }
      builder.append(name);
    }
    builder.append("]");
    String compositeName = builder.toString();
    myCompositeName = compositeName;
    myPresentableName = compositeName;
  }

  @Override
  public @NonNls String getTypeId() {
    return TYPE_ID;
  }

  @Override
  public @Nls String getPresentableName() {
    return myPresentableName;
  }

  public void setPresentableName(@Nls String presentableName) {
    myPresentableName = presentableName;
  }

  public @Nls String getMetaTargetName() {
    return myCompositeName;
  }

  public List<@NlsSafe String> getTargetNames() {
    return myTargetNames;
  }

  @Override
  public void readExternal(Element element, Project project) throws InvalidDataException {
    super.readExternal(element, project);
    @NlsSafe String presentableName = element.getAttributeValue(PRESENTABLE_NAME);
    myPresentableName = presentableName;
  }

  @Override
  public String writeExternal(Element element, Project project) {
    element.setAttribute(PRESENTABLE_NAME, myPresentableName);
    return myCompositeName;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }

    final ExecuteCompositeTargetEvent event = (ExecuteCompositeTargetEvent)o;

    if (!myCompositeName.equals(event.myCompositeName)) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    return myCompositeName.hashCode();
  }
}
