package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.config.AntDefaultIntrospector;
import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntTask;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.Hashtable;
import java.util.Map;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntElementFactory {

  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.psi.impl.AntElementFactory");
  private static Map<Object, AntTaskCreator> antTaskTypeToKnownAntTaskCreatorMap = null;

  private AntElementFactory() {
  }

  @NotNull
  public static AntElement createAntElement(final AntElement parent, final XmlTag tag) {
    instantiate();
    final String elementName = tag.getName();
    if ("target".equals(elementName)) {
      return new AntTargetImpl(parent, tag);
    }
    final Class taskClass = AntDefaultIntrospector.getTaskClass(elementName);
    if (taskClass != null) {
      AntTaskCreator antTaskCreator = antTaskTypeToKnownAntTaskCreatorMap.get(taskClass);
      if (antTaskCreator != null) {
        return antTaskCreator.create(parent, tag);
      }
    }
    if (parent instanceof AntTask) {
      AntTask task = (AntTask)parent;
      final Class elementType = task.getNestedElementType(task.getSourceElement().getName());
      if (elementType != null) {
        AntTaskCreator antTaskCreator = antTaskTypeToKnownAntTaskCreatorMap.get(elementType);
        if (antTaskCreator != null) {
          return antTaskCreator.create(parent, tag);
        }
      }
    }
    return new AntElementImpl(parent, tag);
  }

  private static void instantiate() {
    if (antTaskTypeToKnownAntTaskCreatorMap == null) {
      antTaskTypeToKnownAntTaskCreatorMap = new HashMap<Object, AntTaskCreator>();
      @NonNls final Hashtable taskDefinitions = AntDefaultIntrospector.getTaskDefinitions();
      LOG.assertTrue(taskDefinitions != null);
      Object antTaskClass = taskDefinitions.get("property");
      LOG.assertTrue(antTaskClass != null);
      antTaskTypeToKnownAntTaskCreatorMap.put(antTaskClass, new AntTaskCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag);
        }
      });
      antTaskClass = taskDefinitions.get("antcall");
      LOG.assertTrue(antTaskClass != null);
      antTaskTypeToKnownAntTaskCreatorMap.put(antTaskClass, new AntTaskCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntCallImpl(parent, tag);
        }
      });
    }
  }

  private static interface AntTaskCreator {
    AntElement create(final AntElement parent, final XmlTag tag);
  }
}
