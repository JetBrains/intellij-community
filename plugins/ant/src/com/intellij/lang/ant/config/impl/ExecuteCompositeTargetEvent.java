package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.config.ExecutionEvent;
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
    final List<String> targetNames = new ArrayList<String>();
    while (tokenizer.hasMoreTokens()) {
      targetNames.add(tokenizer.nextToken().trim());
    }
    myTargetNames = targetNames.toArray(new String[targetNames.size()]);
    myPresentableName = myCompositeName;
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

  public void readExternal(Element element) {
    super.readExternal(element);
    myPresentableName = element.getAttributeValue(PRESENTABLE_NAME);
  }

  public void writeExternal(Element element) {
    super.writeExternal(element);
    element.setAttribute(PRESENTABLE_NAME, myPresentableName);
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
