package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.*;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.PsiLock;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class AntScriptDefImpl extends AntTaskImpl implements AntScriptDef {

  @NonNls public static final String ANT_SCRIPTDEF_NAME = "AntScriptDef";

  private AntTypeDefinitionImpl myScriptDefinition;

  public AntScriptDefImpl(final AntStructuredElement parent, final XmlTag sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    invalidateScriptDefinition();
  }

  public String toString() {
    return createScriptClassName(getName());
  }

  public static String createScriptClassName(final String macroName) {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(ANT_SCRIPTDEF_NAME);
      builder.append("[");
      builder.append(macroName);
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.SCRIPTDEF_ROLE;
  }

  public void acceptAntElementVisitor(@NotNull final AntElementVisitor visitor) {
    visitor.visitAntScriptDef(this);
  }

  public AntTypeDefinition getScriptDefinition() {
    return myScriptDefinition;
  }

  public void clearCaches() {
    synchronized (PsiLock.LOCK) {
      super.clearCaches();
      final AntFile file = getAntFile();
      clearClassesCache();
      file.clearCaches();
    }
  }

  public void clearClassesCache() {
    synchronized (PsiLock.LOCK) {
      if (myScriptDefinition != null) {
        final AntFile file = getAntFile();
        final AntAllTasksContainerImpl sequential = PsiTreeUtil.getChildOfType(this, AntAllTasksContainerImpl.class);
        if (sequential != null) {
          sequential.unregisterCustomType(myScriptDefinition);
        }
        for (AntTypeId id : myScriptDefinition.getNestedElements()) {
          final AntTypeDefinition nestedDef = file.getBaseTypeDefinition(myScriptDefinition.getNestedClassName(id));
          if (nestedDef != null) {
            file.unregisterCustomType(nestedDef);
            if (sequential != null) {
              sequential.unregisterCustomType(nestedDef);
            }
          }
        }
        final AntStructuredElement parent = getAntProject();
        if (parent != null) {
          parent.unregisterCustomType(myScriptDefinition);
        }
        myScriptDefinition = null;
      }
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void invalidateScriptDefinition() {
    if (!hasNameElement()) {
      myScriptDefinition = null;
      return;
    }

    final AntFile file = getAntFile();
    if (file == null) return;

    final String thisClassName = createScriptClassName(getName());
    myScriptDefinition = (AntTypeDefinitionImpl)file.getBaseTypeDefinition(thisClassName);
    final Map<String, AntAttributeType> attributes =
      (myScriptDefinition == null) ? new HashMap<String, AntAttributeType>() : myScriptDefinition.getAttributesMap();
    attributes.clear();
    final Map<AntTypeId, String> nestedElements =
      (myScriptDefinition == null) ? new HashMap<AntTypeId, String>() : myScriptDefinition.getNestedElementsMap();
    for (AntElement child : getChildren()) {
      if (child instanceof AntStructuredElement) {
        final AntStructuredElement se = (AntStructuredElement)child;
        final String name = se.getName();
        if (name != null) {
          final XmlTag sourceElement = se.getSourceElement();
          final String tagName = sourceElement.getName();
          if (tagName.equals("attribute")) {
            attributes.put(name.toLowerCase(Locale.US), AntAttributeType.STRING);
          }
          else if (tagName.equals("element")) {
            final String classNameAttrib = sourceElement.getAttributeValue("classname");
            final String elementClassName = classNameAttrib != null? classNameAttrib : thisClassName + '$' + name;
            AntTypeDefinitionImpl nestedDef = (AntTypeDefinitionImpl)file.getBaseTypeDefinition(elementClassName);
            if (nestedDef == null) {
              final AntTypeDefinitionImpl targetDef = (AntTypeDefinitionImpl)file.getTargetDefinition();
              if (targetDef != null) {
                nestedDef = new AntTypeDefinitionImpl(targetDef);
              }
            }
            if (nestedDef != null) {
              final String typeAttrib = sourceElement.getAttributeValue("type");
              final String typeName = typeAttrib != null? typeAttrib : name;
              final AntTypeId typeId = new AntTypeId(typeName);
              nestedDef.setTypeId(typeId);
              nestedDef.setClassName(elementClassName);
              //nestedDef.setIsTask(false);
              nestedDef.setDefiningElement(child);
              file.registerCustomType(nestedDef);
              nestedElements.put(typeId, nestedDef.getClassName());
            }
          }
        }
      }
    }
    final AntTypeId definedTypeId = new AntTypeId(getName());
    if (myScriptDefinition == null) {
      myScriptDefinition = new AntTypeDefinitionImpl(definedTypeId, thisClassName, true, false, attributes, nestedElements, this);
    }
    else {
      myScriptDefinition.setTypeId(definedTypeId);
      myScriptDefinition.setClassName(thisClassName);
      myScriptDefinition.setIsTask(true);
      myScriptDefinition.setDefiningElement(this);
    }
    final AntStructuredElement parent = getAntProject();
    if (parent != null) {
      parent.registerCustomType(myScriptDefinition);
    }
    // define itself as nested task for sequential
    final AntAllTasksContainerImpl sequential = PsiTreeUtil.getChildOfType(this, AntAllTasksContainerImpl.class);
    if (sequential != null) {
      sequential.registerCustomType(myScriptDefinition);
      for (final AntTypeId id : myScriptDefinition.getNestedElements()) {
        sequential.registerCustomType(file.getBaseTypeDefinition(myScriptDefinition.getNestedClassName(id)));
      }
    }
  }
}
