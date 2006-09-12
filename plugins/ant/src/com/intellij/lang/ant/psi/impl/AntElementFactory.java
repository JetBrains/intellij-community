package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.*;
import org.apache.tools.ant.taskdefs.optional.extension.JarLibResolveTask;
import org.apache.tools.ant.taskdefs.optional.perforce.P4Counter;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class AntElementFactory {

  private static Map<String, AntElementCreator> ourAntTypeToKnownAntElementCreatorMap = null;

  private AntElementFactory() {
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  @Nullable
  public static AntElement createAntElement(final AntElement parent, final XmlElement element) {
    instantiate();

    if (element instanceof XmlComment) {
      return new AntCommentImpl(parent, element);
    }
    if (element instanceof XmlEntityRef && parent instanceof AntStructuredElement) {
      return new AntEntityRefImpl((AntStructuredElement)parent, element);
    }
    if (!(element instanceof XmlTag)) return null;

    final XmlTag tag = (XmlTag)element;
    AntTypeDefinition typeDef = null;
    String typeName = tag.getName();

    /**
     * Hardcode for <javadoc> task (IDEADEV-6731).
     */
    if (typeName.equals("javadoc2")) {
      typeName = "javadoc";
    }
    /**
     * Hardcode for <unwar> and <unjar> tasks (IDEADEV-6830).
     */
    if (typeName.equals("unwar") || typeName.equals("unjar")) {
      typeName = "unzip";
    }

    final AntTypeId id = new AntTypeId(typeName);
    final AntFile file = parent.getAntFile();

    if (parent instanceof AntStructuredElement) {
      final AntTypeDefinition parentDef = ((AntStructuredElement)parent).getTypeDefinition();
      if (parentDef != null) {
        final String className = parentDef.getNestedClassName(id);
        if (className != null && file != null) {
          typeDef = file.getBaseTypeDefinition(className);
        }
      }
    }
    if (typeDef == null && file != null) {
      for (AntTypeDefinition def : file.getBaseTypeDefinitions()) {
        if (id.equals(def.getTypeId())) {
          typeDef = def;
          break;
        }
      }
    }
    boolean importedType = false;
    if (typeDef == null) {
      final AntProject project = parent.getAntProject();
      for (final AntFile imported : project.getImportedFiles()) {
        final AntProject importedProject = imported.getAntProject();
        if (!project.equals(importedProject)) {
          importedProject.getChildren();
          for (AntTypeDefinition def : imported.getBaseTypeDefinitions()) {
            if (id.equals(def.getTypeId())) {
              importedType = true;
              typeDef = def;
              break;
            }
          }
        }
      }
    }
    AntStructuredElementImpl result = null;
    if (typeDef != null) {
      final AntElementCreator antElementCreator = ourAntTypeToKnownAntElementCreatorMap.get(typeDef.getClassName());
      if (antElementCreator != null) {
        result = (AntStructuredElementImpl)antElementCreator.create(parent, tag);
      }
      else if (typeDef.isTask()) {
        result = new AntTaskImpl(parent, tag, typeDef);
      }
    }
    if (result == null) {
      // HACK for the <tstamp> properties
      result = (!"format".equals(tag.getName()))
               ? new AntStructuredElementImpl(parent, tag, typeDef)
               : new AntStructuredElementImpl(parent, tag, typeDef, AntFileImpl.PROPERTY) {
                 public String getName() {
                   return super.getName();//getSourceElement().getName();
                 }
               };
    }
    result.setImportedTypeDefinition(importedType);
    return result;

  }

  private static void instantiate() {
    if (ourAntTypeToKnownAntElementCreatorMap == null) {
      ourAntTypeToKnownAntElementCreatorMap = new HashMap<String, AntElementCreator>();
      final AntElementCreator targetCreator = new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntTargetImpl(parent, tag);
        }
      };
      ourAntTypeToKnownAntElementCreatorMap.put(Target.class.getName(), targetCreator);
      ourAntTypeToKnownAntElementCreatorMap.put(Ant.TargetElement.class.getName(), targetCreator);

      // properties
      ourAntTypeToKnownAntElementCreatorMap.put(Property.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Property.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Tstamp.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntPropertyImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Tstamp.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Checksum.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          final AntTypeDefinition checksumDef = parent.getAntFile().getBaseTypeDefinition(Checksum.class.getName());
          if (tag.getAttributeValue("totalproperty") != null) {
            return new AntPropertyImpl(parent, tag, checksumDef, "totalproperty");
          }
          return new AntPropertyImpl(parent, tag, checksumDef, AntFileImpl.PROPERTY);
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(ExecTask.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          final AntTypeDefinition execDef = parent.getAntFile().getBaseTypeDefinition(ExecTask.class.getName());
          if (tag.getAttributeValue("resultproperty") != null) {
            return new AntPropertyImpl(parent, tag, execDef, "resultproperty");
          }
          return new AntPropertyImpl(parent, tag, execDef, "outputproperty");
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Java.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          final AntTypeDefinition execDef = parent.getAntFile().getBaseTypeDefinition(Java.class.getName());
          return new AntPropertyImpl(parent, tag, execDef, "resultproperty");
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Exit.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          final AntTypeDefinition failTaskDefinition = parent.getAntFile().getBaseTypeDefinition(Exit.class.getName());
          if (tag.getAttributeValue("if") != null) {
            return new AntPropertyImpl(parent, tag, failTaskDefinition, "if");
          }
          return new AntPropertyImpl(parent, tag, failTaskDefinition, "unless");
        }
      });
      for (final String clazz : PROPERTY_CLASSES) {
        addPropertyCreator(clazz);
      }

      // sequential and parallel tasks can contain all other tasks
      for (final String clazz : ALL_TASKS_CONTAINER_CLASSES) {
        addAllTasksContainerCreator(clazz);
      }
      ourAntTypeToKnownAntElementCreatorMap.put(CallTarget.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntCallImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(CallTarget.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Taskdef.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntTypeDefImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Taskdef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Typedef.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntTypeDefImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Typedef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(MacroDef.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntMacroDefImpl((AntStructuredElement)parent, tag,
                                     parent.getAntFile().getBaseTypeDefinition(MacroDef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(PreSetDef.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntPresetDefImpl((AntStructuredElement)parent, tag,
                                      parent.getAntFile().getBaseTypeDefinition(PreSetDef.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(ImportTask.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntImportImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(ImportTask.class.getName()));
        }
      });
      ourAntTypeToKnownAntElementCreatorMap.put(Ant.class.getName(), new AntElementCreator() {
        public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
          return new AntAntImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Ant.class.getName()));
        }
      });
    }
  }

  private static void addPropertyCreator(final String className) {
    ourAntTypeToKnownAntElementCreatorMap.put(className, new AntElementCreator() {
      public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
        return new AntPropertyImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(className), AntFileImpl.PROPERTY);
      }
    });
  }

  private static void addAllTasksContainerCreator(final String className) {
    ourAntTypeToKnownAntElementCreatorMap.put(className, new AntElementCreator() {
      public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
        return new AntAllTasksContainerImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(className));
      }
    });
  }

  private final static String[] PROPERTY_CLASSES = {Available.class.getName(), ConditionTask.class.getName(), UpToDate.class.getName(),
    Dirname.class.getName(), Basename.class.getName(), LoadFile.class.getName(), TempFile.class.getName(), PathConvert.class.getName(),
    Length.class.getName(), WhichResource.class.getName(), JarLibResolveTask.class.getName(), P4Counter.class.getName()};

  private final static String[] ALL_TASKS_CONTAINER_CLASSES =
    {MacroDef.NestedSequential.class.getName(), Sequential.class.getName(), Parallel.class.getName()};

  private static interface AntElementCreator {
    AntStructuredElement create(final AntElement parent, final XmlTag tag);
  }
}
