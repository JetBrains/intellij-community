package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.lang.ant.psi.introspection.AntTaskDefinition;
import com.intellij.openapi.util.Pair;
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
    String className = null;
    final Pair<String, String> taskId = new Pair<String, String>(tag.getName(), tag.getNamespace());
    if (parent instanceof AntTask) {
      final AntTaskDefinition def = ((AntTask)parent).getTaskDefinition();
      if (def != null) {
        className = def.getNestedClassName(taskId);
      }
    }
    else {
      for (AntTaskDefinition def : parent.getAntProject().getBaseTaskDefinitions()) {
        if (taskId.equals(def.getTaskId())) {
          className = def.getClassName();
          break;
        }
      }
    }
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
    return new AntTaskImpl(parent, tag, parent.getAntProject().getBaseTaskDefinition(className));
  }

  private static void instantiate() {
    if (antTaskTypeToKnownAntTaskCreatorMap == null) {
      antTaskTypeToKnownAntTaskCreatorMap = new HashMap<String, AntTaskCreator>();
      antTaskTypeToKnownAntTaskCreatorMap.put(Property.class.getName(), new AntTaskCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag, parent.getAntProject().getBaseTaskDefinition(Property.class.getName()));
        }
      });
      antTaskTypeToKnownAntTaskCreatorMap.put(CallTarget.class.getName(), new AntTaskCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntCallImpl(parent, tag, parent.getAntProject().getBaseTaskDefinition(CallTarget.class.getName()));
        }
      });
    }
  }

  private static interface AntTaskCreator {
    AntElement create(final AntElement parent, final XmlTag tag);
  }
}
