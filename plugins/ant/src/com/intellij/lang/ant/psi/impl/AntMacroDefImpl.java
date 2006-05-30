package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntMacroDef;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntAttributeType;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.lang.ant.psi.introspection.impl.AntTypeDefinitionImpl;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.StringBuilderSpinAllocator;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;

public class AntMacroDefImpl extends AntTaskImpl implements AntMacroDef {

  @NonNls public static final String ANT_MACRODEF_NAME = "AntMacroDef";

  private AntTypeDefinition myMacroDefinition;

  public AntMacroDefImpl(final AntStructuredElement parent,
                         final XmlElement sourceElement,
                         final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    invalidateMacroDefinition();
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void invalidateMacroDefinition() {
    if (!hasNameElement()) {
      myMacroDefinition = null;
      return;
    }
    final AntFile file = getAntFile();
    final String thisClassName = toString();
    final HashMap<String, AntAttributeType> attributes = new HashMap<String, AntAttributeType>();
    final HashMap<AntTypeId, String> nestedElements = new HashMap<AntTypeId, String>();
    for (AntElement child : getChildren()) {
      if (child instanceof AntStructuredElement) {
        AntStructuredElement se = (AntStructuredElement)child;
        final String name = se.getName();
        if (name != null) {
          final String tagName = se.getSourceElement().getName();
          if (tagName.equals("attribute")) {
            attributes.put(name, AntAttributeType.STRING);
          }
          else if (tagName.equals("element")) {
            final AntTypeDefinitionImpl nestedDef =
              new AntTypeDefinitionImpl((AntTypeDefinitionImpl)file.getTargetDefinition());
            final AntTypeId typeId = new AntTypeId(name);
            nestedDef.setTypeId(typeId);
            nestedDef.setIsTask(false);
            nestedDef.setClassName(thisClassName + '.' + name);
            nestedDef.setDefiningElement(child);
            file.registerCustomType(nestedDef);
            nestedElements.put(typeId, nestedDef.getClassName());
          }
        }
      }
    }
    myMacroDefinition = new AntTypeDefinitionImpl(new AntTypeId(getName()), thisClassName, true, attributes,
                                                  nestedElements, this);
    getAntParent().registerCustomType(myMacroDefinition);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
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

  public AntStructuredElement getAntParent() {
    return (AntStructuredElement)super.getAntParent();
  }

  public AntTypeDefinition getMacroDefinition() {
    return myMacroDefinition;
  }

  public void clearCaches() {
    super.clearCaches();
    if (myMacroDefinition != null) {
      final AntFile file = getAntFile();
      for (AntTypeId id : myMacroDefinition.getNestedElements()) {
        final AntTypeDefinition nestedDef =
          file.getBaseTypeDefinition(myMacroDefinition.getNestedClassName(id));
        file.unregisterCustomType(nestedDef);
      }
      getAntParent().unregisterCustomType(myMacroDefinition);
      invalidateMacroDefinition();
    }
  }
}
