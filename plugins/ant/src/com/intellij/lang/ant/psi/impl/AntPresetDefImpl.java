package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntPresetDef;
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

public class AntPresetDefImpl extends AntAllTasksContainerImpl implements AntPresetDef {

  @NonNls public static final String ANT_PRESETDEF_NAME = "AntPresetDef";

  private AntTypeDefinitionImpl myPresetDefinition;

  public AntPresetDefImpl(final AntStructuredElement parent, final XmlElement sourceElement, final AntTypeDefinition definition) {
    super(parent, sourceElement, definition);
    invalidatePresetDefinition();
  }

  public String toString() {
    @NonNls final StringBuilder builder = StringBuilderSpinAllocator.alloc();
    try {
      builder.append(ANT_PRESETDEF_NAME);
      builder.append("[");
      builder.append(getName());
      builder.append("]");
      return builder.toString();
    }
    finally {
      StringBuilderSpinAllocator.dispose(builder);
    }
  }

  public AntTypeDefinition getPresetDefinition() {
    return myPresetDefinition;
  }

  public void clearCaches() {
    super.clearCaches();
    if (myPresetDefinition != null) {
      getAntParent().unregisterCustomType(myPresetDefinition);
      myPresetDefinition = null;
      getAntFile().clearCaches();
    }
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  private void invalidatePresetDefinition() {
    myPresetDefinition = null;
    if (!hasNameElement()) return;
    final String thisClassName = toString();
    final AntStructuredElementImpl extented = PsiTreeUtil.getChildOfType(this, AntStructuredElementImpl.class);
    final AntTypeId typeId = new AntTypeId(getName());
    if (extented != null) {
      final AntTypeDefinitionImpl extentedDef = (AntTypeDefinitionImpl)extented.getTypeDefinition();
      if (extentedDef != null) {
        myPresetDefinition = new AntTypeDefinitionImpl(typeId, thisClassName, extentedDef.isTask(), extentedDef.getAttributesMap(),
                                                       extentedDef.getNestedElementsMap(), this);
      }
    }
    if (myPresetDefinition == null) {
      myPresetDefinition = new AntTypeDefinitionImpl(typeId, thisClassName, false, new HashMap<String, AntAttributeType>(),
                                                     new HashMap<AntTypeId, String>(), this);
    }
    getAntParent().registerCustomType(myPresetDefinition);
  }
}
