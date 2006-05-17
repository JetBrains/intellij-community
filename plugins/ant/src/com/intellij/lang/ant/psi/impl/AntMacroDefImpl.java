package com.intellij.lang.ant.psi.impl;

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

public class AntMacroDefImpl extends AntStructuredElementImpl implements AntMacroDef {

  private final AntTypeDefinition myMacroDefinition;

  @SuppressWarnings({"HardCodedStringLiteral"})
  public AntMacroDefImpl(final AntElement parent,
                         final XmlElement sourceElement,
                         final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);

    if (getNameElement() == ourNull) {
      myMacroDefinition = null;
      return;
    }

    final AntFile file = getAntFile();
    final AntStructuredElement seParent = PsiTreeUtil.getParentOfType(this, AntStructuredElement.class);
    assert seParent != null;
    final String thisClassName = toString();
    final HashMap<String, AntAttributeType> attributes = new HashMap<String, AntAttributeType>();
    final HashMap<AntTypeId, String> nestedElements = new HashMap<AntTypeId, String>();
    final AntElement[] children = getChildren();

    for (AntElement child : children) {
      if (child instanceof AntStructuredElement) {
        AntStructuredElement se = (AntStructuredElement)child;
        final String name = se.getName();
        if (name != null) {
          final String tagName = se.getSourceElement().getName();
          if (tagName.equals("attribute")) {
            attributes.put(name, AntAttributeType.STRING);
          }
          else if (tagName.equals("element")) {
            final AntTypeDefinitionImpl nestedElementDef =
              new AntTypeDefinitionImpl((AntTypeDefinitionImpl)file.getTargetDefinition());
            final AntTypeId typeId = new AntTypeId(name);
            nestedElementDef.setTypeId(typeId);
            nestedElementDef.setIsTask(false);
            nestedElementDef.setClassName(thisClassName + '.' + name);
            seParent.registerCustomType(nestedElementDef);
            nestedElements.put(typeId, nestedElementDef.getClassName());
          }
        }
      }
    }
    final String name = getName();
    myMacroDefinition =
      new AntTypeDefinitionImpl(new AntTypeId(name), thisClassName, true, attributes, nestedElements);
    seParent.registerCustomType(myMacroDefinition);
  }

  public String toString() {
    @NonNls StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append("AntMacroDef[");
      builder.append(getSourceElement().getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntTypeDefinition getMacroDefinition() {
    return myMacroDefinition;
  }
}
