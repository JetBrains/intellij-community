package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;

public class AntTaskImpl extends AntElementImpl implements AntTask {

  private AntTaskDefinition myDefinition;

  public AntTaskImpl(final AntElement parent, final XmlElement sourceElement, final AntTaskDefinition definition) {
    super(parent, sourceElement);
    myDefinition = definition;
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntTask[");
      builder.append(getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  public String getName() {
    return getSourceElement().getName();
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    throw new IncorrectOperationException();
  }

  public AntTaskDefinition getTaskDefinition() {
    return myDefinition;
  }

  private boolean myDefinitionCloned = false;

  protected void registerCustomTask(final String name, final String namespace, final AntTaskDefinition definition) {
    if (myDefinition != null) {
      if (!myDefinitionCloned) myDefinition = myDefinition.clone();
      myDefinition.registerNestedTask(definition.getClassName());
      if (myTaskIdToClassMap == null) {
        myTaskIdToClassMap = new HashMap<String, String>();
      }
      myTaskIdToClassMap.put(namespace + name, definition.getClassName());
      getAntProject().registerCustomTask(name, namespace, definition);
    }
  }
}
