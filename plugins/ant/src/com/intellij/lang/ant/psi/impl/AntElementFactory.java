package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.*;
import org.apache.tools.ant.taskdefs.optional.extension.JarLibResolveTask;
import org.apache.tools.ant.taskdefs.optional.perforce.P4Counter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@SuppressWarnings({"UseOfObsoleteCollectionType"})
public class AntElementFactory {

  private static Map<String, AntElementCreator> ourAntTypeToKnownAntElementCreatorMap = null;

  private AntElementFactory() {
  }

  @Nullable
  public static AntElement createAntElement(final AntElement parent, final XmlElement element) {
    instantiate();
    if (element instanceof XmlComment) return new AntCommentImpl(parent, element);
    if (!(element instanceof XmlTag)) return null;
    XmlTag tag = (XmlTag)element;
    AntTypeDefinition typeDef = null;
    final AntTypeId id = new AntTypeId(tag.getName());
    final AntFile file = parent.getAntFile();

    while (true) {
      if (parent instanceof AntStructuredElement) {
        final AntTypeDefinition parentDef = ((AntStructuredElement)parent).getTypeDefinition();
        if (parentDef != null) {
          final String className = parentDef.getNestedClassName(id);
          if (className != null) {
            typeDef = file.getBaseTypeDefinition(className);
          }
        }
      }
      if (typeDef == null) {
        for (AntTypeDefinition def : file.getBaseTypeDefinitions()) {
          if (id.equals(def.getTypeId())) {
            typeDef = def;
            break;
          }
        }
      }
      if (typeDef == null) {
        final AntFileImpl antFile = (AntFileImpl)file;
        if (!antFile.getSecondPassMade()) {
          antFile.invokeSecondPass();
          continue;
        }
      }
      else {
        AntElementCreator antElementCreator =
          ourAntTypeToKnownAntElementCreatorMap.get(typeDef.getClassName());
        if (antElementCreator != null) {
          return antElementCreator.create(parent, tag);
        }
        if (typeDef.isTask()) {
          return new AntTaskImpl(parent, tag, typeDef);
        }
      }
      return new AntStructuredElementImpl(parent, tag, typeDef);
    }
  }

  private static void instantiate() {
    if (ourAntTypeToKnownAntElementCreatorMap == null) {
      ourAntTypeToKnownAntElementCreatorMap = new HashMap<String, AntElementCreator>();
      final AntElementCreator targetCreator = new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntTargetImpl(parent, tag);
        }
      };
      ourAntTypeToKnownAntElementCreatorMap.put(Target.class.getName(), targetCreator);
      ourAntTypeToKnownAntElementCreatorMap.put(Ant.TargetElement.class.getName(), targetCreator);
      ourAntTypeToKnownAntElementCreatorMap.put(Property.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag,
                                     parent.getAntFile().getBaseTypeDefinition(Property.class.getName()));
        }
      });
      for (final String clazz : PROPERTY_CLASSES) {
        addPropertyCreator(clazz);
      }
      for (final String clazz : ALL_TASKS_CONTAINER_CLASSES) {
        addAllTasksContainerCreator(clazz);
      }
      ourAntTypeToKnownAntElementCreatorMap.put(CallTarget.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntCallImpl(parent, tag,
                                 parent.getAntFile().getBaseTypeDefinition(CallTarget.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Taskdef.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntTypeDefImpl(parent, tag,
                                    parent.getAntFile().getBaseTypeDefinition(Taskdef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Typedef.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntTypeDefImpl(parent, tag,
                                    parent.getAntFile().getBaseTypeDefinition(Typedef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(MacroDef.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntMacroDefImpl(parent, tag,
                                     parent.getAntFile().getBaseTypeDefinition(MacroDef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(ImportTask.class.getName(), new AntElementCreator() {
        public AntElement create(final AntElement parent, final XmlTag tag) {
          return new AntImportImpl(parent, tag,
                                   parent.getAntFile().getBaseTypeDefinition(ImportTask.class.getName()));
        }
      });
    }
  }

  private static void addPropertyCreator(final String className) {
    ourAntTypeToKnownAntElementCreatorMap.put(className, new AntElementCreator() {
      public AntElement create(final AntElement parent, final XmlTag tag) {
        return new AntPropertyImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(className),
                                   "property");
      }
    });
  }

  private static void addAllTasksContainerCreator(final String className) {
    ourAntTypeToKnownAntElementCreatorMap.put(className, new AntElementCreator() {
      public AntElement create(final AntElement parent, final XmlTag tag) {
        return new AntAllTasksContainerImpl(parent, tag,
                                            parent.getAntFile().getBaseTypeDefinition(className));
      }
    });
  }

  private final static String[] PROPERTY_CLASSES = {Available.class.getName(), Checksum.class.getName(),
    ConditionTask.class.getName(), UpToDate.class.getName(), Dirname.class.getName(),
    Basename.class.getName(), LoadFile.class.getName(), TempFile.class.getName(), PathConvert.class.getName(),
    Length.class.getName(), WhichResource.class.getName(), JarLibResolveTask.class.getName(),
    P4Counter.class.getName()};

  private final static String[] ALL_TASKS_CONTAINER_CLASSES =
    {MacroDef.NestedSequential.class.getName(), Sequential.class.getName(), Parallel.class.getName()};

  private static interface AntElementCreator {
    AntElement create(final AntElement parent, final XmlTag tag);
  }
}
