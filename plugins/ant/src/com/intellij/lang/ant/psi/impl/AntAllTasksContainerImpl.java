package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntAllTasksContainer;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.psi.xml.XmlElement;

public class AntAllTasksContainerImpl extends AntTaskImpl implements AntAllTasksContainer {

  public AntAllTasksContainerImpl(final AntElement parent,
                                  final XmlElement sourceElement,
                                  final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    if (definition.getNestedElements().length == 0) {
      // allow all tasks as nested elements
      for (AntTypeDefinition def : getAntFile().getBaseTypeDefinitions()) {
        if (def.isTask()) {
          definition.registerNestedType(def.getTypeId(), def.getClassName());
        }
      }
    }
  }
}
