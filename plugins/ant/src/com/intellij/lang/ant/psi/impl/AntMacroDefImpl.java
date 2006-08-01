package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.AntElementRole;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public class AntMacroDefImpl extends AntTaskImpl implements AntMacroDef {

  @NonNls public static final String ANT_MACRODEF_NAME = "AntMacroDef";

  private AntTypeDefinitionImpl myMacroDefinition;

  public AntMacroDefImpl(final AntStructuredElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    invalidateMacroDefinition();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(ANT_MACRODEF_NAME);
      builder.append("[");
      builder.append(getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntElementRole getRole() {
    return AntElementRole.MACRODEF_ROLE;
  }


  public AntTypeDefinition getMacroDefinition() {
    return myMacroDefinition;
  }

  public void clearCaches() {
    super.clearCaches();
    if (myMacroDefinition != null) {
      final AntFile file = getAntFile();
      for (AntTypeId id : myMacroDefinition.getNestedElements()) {
        final AntTypeDefinition nestedDef = file.getBaseTypeDefinition(myMacroDefinition.getNestedClassName(id));
        file.unregisterCustomType(nestedDef);
      }
      getAntParent().unregisterCustomType(myMacroDefinition);
      myMacroDefinition = null;
      file.clearCaches();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void invalidateMacroDefinition() {
    if (!hasNameElement()) {
      myMacroDefinition = null;
      return;
    }
    final AntFile file = getAntFile();
    final String thisClassName = toString();
    myMacroDefinition = (AntTypeDefinitionImpl)file.getBaseTypeDefinition(thisClassName);
    final Map<String, AntAttributeType> attributes =
      (myMacroDefinition == null) ? new HashMap<String, AntAttributeType>() : myMacroDefinition.getAttributesMap();
    final Map<AntTypeId, String> nestedElements =
      (myMacroDefinition == null) ? new HashMap<AntTypeId, String>() : myMacroDefinition.getNestedElementsMap();
    for (AntElement child : getChildren()) {
      if (child instanceof AntStructuredElement) {
        final AntStructuredElement se = (AntStructuredElement)child;
        final String name = se.getName();
        if (name != null) {
          final String tagName = se.getSourceElement().getName();
          if (tagName.equals("attribute")) {
            attributes.put(name, AntAttributeType.STRING);
          }
          else if (tagName.equals("element")) {
            final String elementClassName = thisClassName + '$' + name;
            AntTypeDefinitionImpl nestedDef = (AntTypeDefinitionImpl)file.getBaseTypeDefinition(elementClassName);
            if (nestedDef == null) {
              nestedDef = new AntTypeDefinitionImpl((AntTypeDefinitionImpl)file.getTargetDefinition());
            }
            final AntTypeId typeId = new AntTypeId(name);
            nestedDef.setTypeId(typeId);
            nestedDef.setClassName(elementClassName);
            nestedDef.setIsTask(false);
            nestedDef.setDefiningElement(child);
            file.registerCustomType(nestedDef);
            nestedElements.put(typeId, nestedDef.getClassName());
          }
        }
      }
    }
    final AntTypeId definedTypeId = new AntTypeId(getName());
    if (myMacroDefinition == null) {
      myMacroDefinition = new AntTypeDefinitionImpl(definedTypeId, thisClassName, true, attributes, nestedElements, this);
    }
    else {
      myMacroDefinition.setTypeId(definedTypeId);
      myMacroDefinition.setClassName(thisClassName);
      myMacroDefinition.setIsTask(true);
      myMacroDefinition.setDefiningElement(this);
    }
    getAntParent().registerCustomType(myMacroDefinition);
    // define itself as nested task for sequential
    final AntAllTasksContainerImpl sequential = PsiTreeUtil.getChildOfType(this, AntAllTasksContainerImpl.class);
    if (sequential != null) {
      final AntTypeDefinition sequentialDef = sequential.getTypeDefinition();
      if (sequentialDef != null) {
        sequentialDef.registerNestedType(definedTypeId, thisClassName);
      }
    }
  }
}
