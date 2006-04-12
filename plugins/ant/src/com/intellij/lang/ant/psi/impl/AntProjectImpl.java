package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntTarget;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.lang.ant.psi.introspection.impl.AntTaskDefinitionImpl;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiReference;
import com.intellij.psi.impl.source.resolve.reference.ReferenceType;
import com.intellij.psi.impl.source.resolve.reference.impl.GenericReference;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.IncorrectOperationException;
import com.intellij.util.StringBuilderSpinAllocator;
import org.apache.tools.ant.IntrospectionHelper;
import org.apache.tools.ant.Project;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntProjectImpl extends AntElementImpl implements AntProject {
  final static AntTarget[] EMPTY_TARGETS = new AntTarget[0];

  private AntTarget[] myTargets;
  /**
   * Map of class names to task definitions.
   */
  private Map<String, AntTaskDefinition> myTaskDefinitions;
  private AntTaskDefinition[] myTaskDefinitionArray;

  public AntProjectImpl(final AntFileImpl parent, final XmlTag tag) {
    super(parent, tag);
  }

  @NonNls
  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntProject: ");
      builder.append(getName());
      if (getDescription() != null) {
        builder.append(" [");
        builder.append(getDescription());
        builder.append("]");
      }
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }


  public void clearCaches() {
    myTargets = null;
  }

  @NotNull
  public XmlTag getSourceElement() {
    return (XmlTag)super.getSourceElement();
  }

  @Nullable
  public String getName() {
    return getSourceElement().getAttributeValue("name");
  }

  public PsiElement setName(String name) throws IncorrectOperationException {
    getSourceElement().setAttribute("name", name);
    subtreeChanged();
    return this;
  }

  @Nullable
  public String getBaseDir() {
    return getSourceElement().getAttributeValue("basedir");
  }

  @Nullable
  public String getDescription() {
    final XmlTag tag = getSourceElement().findFirstSubTag("description");
    return tag != null ? tag.getValue().getTrimmedText() : null;
  }

  @NotNull
  public AntTarget[] getTargets() {
    if (myTargets != null) return myTargets;
    final List<AntTarget> targets = new ArrayList<AntTarget>();
    for (final AntElement child : getChildren()) {
      if (child instanceof AntTarget) targets.add((AntTarget)child);
    }
    return myTargets = targets.toArray(new AntTarget[targets.size()]);
  }

  @Nullable
  public AntTarget getDefaultTarget() {
    final PsiReference[] references = getReferences();
    for (PsiReference ref : references) {
      final GenericReference reference = (GenericReference)ref;
      if (reference.getType().isAssignableTo(ReferenceType.ANT_TARGET)) {
        return (AntTarget)reference.resolve();
      }
    }
    return null;
  }

  @Nullable
  public AntTarget getTarget(final String name) {
    AntTarget[] targets = getTargets();
    for (AntTarget target : targets) {
      if (name.equals(target.getName())) {
        return target;
      }
    }
    return null;
  }

  @NotNull
  public AntTaskDefinition[] getTaskDefinitions() {
    if (myTaskDefinitionArray != null) return myTaskDefinitionArray;
    getTaskDefinition(null);
    return myTaskDefinitionArray = myTaskDefinitions.values().toArray(new AntTaskDefinition[myTaskDefinitions.size()]);
  }

  @Nullable
  public AntTaskDefinition getTaskDefinition(final String taskClassName) {
    if (myTaskDefinitions != null) return myTaskDefinitions.get(taskClassName);
    myTaskDefinitions = new HashMap<String, AntTaskDefinition>();
    Project project = new Project();
    project.init();
    final Hashtable ht = project.getTaskDefinitions();
    if (ht == null) return null;
    // first pass creates taskdefinitons without nested elements
    int index = 0;
    final Enumeration tasks = ht.keys();
    while (tasks.hasMoreElements()) {
      final String taskName = (String)tasks.nextElement();
      final Class taskClass = (Class)ht.get(taskName);
      final IntrospectionHelper helper = IntrospectionHelper.getHelper(taskClass);
      final HashMap<String, AntAttributeType> attributes = new HashMap<String, AntAttributeType>();
      final Enumeration attrEnum = helper.getAttributes();
      while (attrEnum.hasMoreElements()) {
        String attr = (String)attrEnum.nextElement();
        final Class attrClass = helper.getAttributeType(attr);
        if (int.class.equals(attrClass)) {
          attributes.put(attr, AntAttributeType.INTEGER);
        }
        else if (boolean.class.equals(attrClass)) {
          attributes.put(attr, AntAttributeType.BOOLEAN);
        }
        else {
          attributes.put(attr, AntAttributeType.STRING);
        }
      }
      AntTaskDefinition def = new AntTaskDefinitionImpl(this, taskName, getSourceElement().getNamespace(), taskClass.getName(), attributes);
      myTaskDefinitions.put(def.getClassName(), def);
    }

    // second pass updates nested elements of known task definitions
    for (AntTaskDefinition def : myTaskDefinitions.values()) {
      final Class taskClass = (Class)ht.get(def.getName());
      final IntrospectionHelper helper = IntrospectionHelper.getHelper(taskClass);
      final Enumeration nestedEnum = helper.getNestedElements();
      while (nestedEnum.hasMoreElements()) {
        final String nestedElement = (String)nestedEnum.nextElement();
        def.registerNestedTask(getTaskClassByName(nestedElement, def.getNamespace()));
      }
    }
    return myTaskDefinitions.get(taskClassName);
  }

  public void registerCustomTask(final String name, final String namespace, final AntTaskDefinition definition) {
    myTaskIdToClassMap = null;
    myTaskDefinitionArray = null;
    myTaskDefinitions.put(definition.getClassName(), definition);
    myTaskIdToClassMap.put(namespace + name, definition.getClassName());
  }

  public String getTaskClassByName(final String name, final String namespace) {
    return myTaskIdToClassMap.get(namespace + name);
  }
}
