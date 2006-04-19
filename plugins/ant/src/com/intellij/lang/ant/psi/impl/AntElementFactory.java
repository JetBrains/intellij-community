package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.CallTarget;
import org.apache.tools.ant.taskdefs.Property;
import org.apache.tools.ant.taskdefs.Taskdef;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntElementFactory {

  private static Map<String, AntElementCreator> ourAntTypeToKnownAntElementCreatorMap = null;

  private AntElementFactory() {
  }

  @NotNull
  public static AntElement createAntElement(final AntElement parent, final XmlTag tag) {
    instantiate();
    AntTypeDefinition typeDef = null;
    final AntTypeId id = new AntTypeId(tag.getName(), tag.getNamespace());
    final AntProject project = parent.getAntProject();
    if (parent instanceof AntStructuredElement) {
      final AntTypeDefinition parentDef = ((AntStructuredElement)parent).getTypeDefinition();
      if (parentDef != null) {
        final String className = parentDef.getNestedClassName(id);
        if (className != null) {
          typeDef = project.getBaseTypeDefinition(className);
        }
      }
    }
    if (typeDef == null) {
      for (AntTypeDefinition def : project.getBaseTypeDefinitions()) {
        if (id.equals(def.getTypeId())) {
          typeDef = def;
          break;
        }
      }
    }
    if (typeDef != null) {
      AntElementCreator antElementCreator = ourAntTypeToKnownAntElementCreatorMap.get(typeDef.getClassName());
      if (antElementCreator != null) {
        return antElementCreator.create(parent, tag);
      }
      if (typeDef.isTask()) {
        return new AntTaskImpl(parent, tag, typeDef);
      }
    }
    return new AntStructuredElementImpl(parent, tag, typeDef);
  }

  private static void instantiate() {
    if (ourAntTypeToKnownAntElementCreatorMap == null) {
      ourAntTypeToKnownAntElementCreatorMap = new HashMap<String, AntElementCreator>();
      ourAntTypeToKnownAntElementCreatorMap.put(Target.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntTargetImpl(parent, tag);
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Property.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag, parent.getAntProject().getBaseTypeDefinition(Property.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(CallTarget.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntCallImpl(parent, tag, parent.getAntProject().getBaseTypeDefinition(CallTarget.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Taskdef.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntTaskDefImpl(parent, tag, parent.getAntProject().getBaseTypeDefinition(CallTarget.class.getName()));
        }
      });
    }
  }

  private static interface AntElementCreator {
    AntElement create(final AntElement parent, final XmlTag tag);
  }
}
