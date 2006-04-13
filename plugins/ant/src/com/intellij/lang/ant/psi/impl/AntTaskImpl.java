package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.lang.ant.psi.introspection.impl.AntTaskDefinitionImpl;
import com.intellij.openapi.util.Pair;
import com.intellij.psi.PsiElement;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

public class AntTaskImpl extends AntElementImpl implements AntTask {

  private AntTaskDefinitionImpl myDefinition;
  private boolean myDefinitionCloned = false;

  public AntTaskImpl(final AntElement parent, final XmlElement sourceElement, final AntTaskDefinition definition) {
    super(parent, sourceElement);
    myDefinition = (AntTaskDefinitionImpl)definition;
    final Pair<String, String> taskId = new Pair<String, String>(getSourceElement().getName(), getSourceElement().getNamespace());
    if (definition != null && !definition.getTaskId().equals(taskId)) {
      myDefinition = new AntTaskDefinitionImpl(myDefinition);
      myDefinition.setTaskId(taskId);
      myDefinitionCloned = true;
    }
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

  protected void registerCustomTask(final AntTaskDefinition def) {
    if (myDefinition != null) {
      if (!myDefinitionCloned) {
        myDefinition = new AntTaskDefinitionImpl(myDefinition);
        myDefinitionCloned = true;
      }
      myDefinition.registerNestedTask(def.getTaskId(), def.getClassName());
      getAntProject().registerCustomTask(def);
    }
  }
}
