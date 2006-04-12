package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.apache.tools.ant.taskdefs.CallTarget;
import org.apache.tools.ant.taskdefs.Property;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntElementFactory {

  private static Map<String, AntTaskCreator> antTaskTypeToKnownAntTaskCreatorMap = null;

  private AntElementFactory() {
  }

  @NotNull
  public static AntElement createAntElement(final AntElement parent, final XmlTag tag) {
    instantiate();
    final String className = parent.getTaskClassByName(tag.getName(), tag.getNamespace());
    return createAntTask(tag, parent, className);
  }

  @NotNull
  public static AntElement createAntTask(final XmlTag tag, final AntElement parent, final String className) {
    instantiate();
    final String elementName = tag.getName();
    if ("target".equals(elementName)) {
      return new AntTargetImpl(parent, tag);
    }
    AntTaskCreator antTaskCreator = antTaskTypeToKnownAntTaskCreatorMap.get(className);
    if (antTaskCreator != null) {
      return antTaskCreator.create(parent, tag);
    }
    return new AntTaskImpl(parent, tag, parent.getAntProject().getTaskDefinition(className));
  }

  private static void instantiate() {
    if (antTaskTypeToKnownAntTaskCreatorMap == null) {
      antTaskTypeToKnownAntTaskCreatorMap = new HashMap<String, AntTaskCreator>();
      antTaskTypeToKnownAntTaskCreatorMap.put(Property.class.getName(), new AntTaskCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag, parent.getAntProject().getTaskDefinition(Property.class.getName()));
        }
      });
      antTaskTypeToKnownAntTaskCreatorMap.put(CallTarget.class.getName(), new AntTaskCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntCallImpl(parent, tag, parent.getAntProject().getTaskDefinition(CallTarget.class.getName()));
        }
      });
    }
  }

  private static interface AntTaskCreator {
    AntElement create(final AntElement parent, final XmlTag tag);
  }
}
