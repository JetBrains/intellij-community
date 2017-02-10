/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

import com.intellij.lang.ant.config.ExecutionEvent;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.ArrayUtil;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public final class ExecuteCompositeTargetEvent extends ExecutionEvent {
  @NonNls public static final String TYPE_ID = "compositeTask";
  private final String myCompositeName;
  private String myPresentableName;
  private final String[] myTargetNames;
  @NonNls public static final String PRESENTABLE_NAME = "presentableName";


  public ExecuteCompositeTargetEvent(final String compositeName) throws WrongNameFormatException {
    if (!(compositeName.startsWith("[") && compositeName.endsWith("]") && compositeName.length() > 2)) {
      throw new WrongNameFormatException(compositeName);
    }
    myCompositeName = compositeName;
    final StringTokenizer tokenizer = new StringTokenizer(compositeName.substring(1, compositeName.length() - 1), ",", false);
    final List<String> targetNames = new ArrayList<>();
    while (tokenizer.hasMoreTokens()) {
      targetNames.add(tokenizer.nextToken().trim());
    }
    myTargetNames = ArrayUtil.toStringArray(targetNames);
    myPresentableName = compositeName;
  }

  public ExecuteCompositeTargetEvent(String[] targetNames) {
    myTargetNames = targetNames;
    final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("[");
      for (int idx = 0; idx < targetNames.length; idx++) {
        if (idx > 0) {
          builder.append(",");
        }
        builder.append(targetNames[idx]);
      }
      builder.append("]");
      myCompositeName = builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
    myPresentableName = myCompositeName;
  }

  public String getTypeId() {
    return TYPE_ID;
  }

  public String getPresentableName() {
    return myPresentableName;
  }

  public void setPresentableName(String presentableName) {
    myPresentableName = presentableName;
  }

  public String getMetaTargetName() {
    return myCompositeName;
  }

  public String[] getTargetNames() {
    return myTargetNames;
  }

  public void readExternal(Element element, Project project) throws InvalidDataException {
    super.readExternal(element, project);
    myPresentableName = element.getAttributeValue(PRESENTABLE_NAME);
  }

  public String writeExternal(Element element, Project project) {
    element.setAttribute(PRESENTABLE_NAME, myPresentableName);
    return myCompositeName;
  }

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

  public int hashCode() {
    return myCompositeName.hashCode();
  }
}
