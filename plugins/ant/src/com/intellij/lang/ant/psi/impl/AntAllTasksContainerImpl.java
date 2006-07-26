package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntAllTasksContainer;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;

public class AntAllTasksContainerImpl extends AntTaskImpl implements AntAllTasksContainer {

  public AntAllTasksContainerImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntAllTasksContainer[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public void init() {
    super.init();
    if (myDefinition.getNestedElements().length == 0) {
      // allow all tasks as nested elements
      for (AntTypeDefinition def : getAntFile().getBaseTypeDefinitions()) {
        if (def.isTask()) {
          myDefinition.registerNestedType(def.getTypeId(), def.getClassName());
        }
      }
    }
  }
}
