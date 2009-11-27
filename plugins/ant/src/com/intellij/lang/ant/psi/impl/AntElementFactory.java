/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.lang.ant.psi.impl;

import com.intellij.lang.ant.psi.AntElement;
import com.intellij.lang.ant.psi.AntFile;
import com.intellij.lang.ant.psi.AntProject;
import com.intellij.lang.ant.psi.AntStructuredElement;
import com.intellij.lang.ant.psi.introspection.AntTypeDefinition;
import com.intellij.lang.ant.psi.introspection.AntTypeId;
import com.intellij.psi.PsiLock;
import com.intellij.psi.xml.XmlComment;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlEntityRef;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.HashMap;
import org.apache.tools.ant.Target;
import org.apache.tools.ant.taskdefs.*;
import org.apache.tools.ant.taskdefs.optional.extension.JarLibResolveTask;
import org.apache.tools.ant.taskdefs.optional.perforce.P4Counter;
import org.apache.tools.ant.taskdefs.optional.script.ScriptDef;
import org.apache.tools.ant.types.DirSet;
import org.apache.tools.ant.types.FileList;
import org.apache.tools.ant.types.FileSet;
import org.apache.tools.ant.types.Path;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

public class AntElementFactory {

  @NonNls private static final String RESULT_PROPERTY = "resultproperty";
  @NonNls private static final String TOTAL_PROPERTY = "totalproperty";

  private static Map<String, AntElementCreator> ourAntTypeToKnownAntElementCreatorMap = null;

  private AntElementFactory() {
  }

  @Nullable
  public static AntElement createAntElement(final AntStructuredElement parent, final XmlElement element) {
    instantiate();

    if (element instanceof XmlComment) {
      return new AntCommentImpl(parent, element);
    }
    if (element instanceof XmlEntityRef) {
      return new AntEntityRefImpl(parent, element);
    }
    if (!(element instanceof XmlTag)) return null;

    final XmlTag tag = (XmlTag)element;
    AntTypeDefinition typeDef = null;
    String typeName = tag.getLocalName();
    final String nsPrefix = tag.getNamespacePrefix();
    if (nsPrefix.length() == 0 ) {
      /**
       * Hardcode for <javadoc> task (IDEADEV-6731).
       */
      if (typeName.equals(AntFileImpl.JAVADOC2_TAG)) {
        typeName = AntFileImpl.JAVADOC2_TAG;
      }
      /**
       * Hardcode for <unwar> and <unjar> tasks (IDEADEV-6830).
       */
      if (typeName.equals(AntFileImpl.UNWAR_TAG) || typeName.equals(AntFileImpl.UNJAR_TAG)) {
        typeName = AntFileImpl.UNZIP_TAG;
      }
    }

    final AntTypeId id = new AntTypeId(typeName, nsPrefix);
    final AntFile file = parent.getAntFile();

    final AntTypeDefinition parentDef = parent.getTypeDefinition();
    if (parentDef != null) {
      final String className = parentDef.getNestedClassName(id);
      if (className != null && file != null) {
        typeDef = file.getBaseTypeDefinition(className);
      }
    }

    if (file != null) {
      // try macrodef
      if (typeDef == null) {
        typeDef = file.getBaseTypeDefinition(AntMacroDefImpl.createMacroClassName(typeName));
      }

      // try preetDef
      if (typeDef == null) {
        typeDef = file.getBaseTypeDefinition(AntPresetDefImpl.createPresetDefClassName(typeName));
      }

      if (typeDef == null) {
        final AntProject project = file.getAntProject();
        final AntTypeDefinition projectDef = project != null? project.getTypeDefinition() : null;
        for (AntTypeDefinition def : file.getBaseTypeDefinitions()) {
          if (id.equals(def.getTypeId())) {
            if (projectDef != null) { // perform additional check if project exists 
              final boolean isRegisteredWithinProject = def.getClassName().equals(projectDef.getNestedClassName(def.getTypeId()));
              if (!isRegisteredWithinProject) {
                continue;
              }
            }
            typeDef = def;
            break;
          }
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
        if (typeDef.isProperty()) {
          result = new AntPropertyImpl(parent, tag, typeDef);
        }
        else if (typeDef.isAllTaskContainer()) {
          result = new AntAllTasksContainerImpl(parent, tag, typeDef);
        }
        else {
          result = new AntTaskImpl(parent, tag, typeDef);
        }
      }
    }
    if (result == null) {
      // HACK for the <tstamp> properties
      result = AntFileImpl.FORMAT_TAG.equals(tag.getName())
               ? new AntTimestampFormatImpl(parent, tag, typeDef)
               : new AntStructuredElementImpl(parent, tag, typeDef);
    }
    result.setImportedTypeDefinition(importedType);
    return result;

  }

  private static void instantiate() {
    synchronized (PsiLock.LOCK) {
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
            if (tag.getAttributeValue(TOTAL_PROPERTY) != null) {
              return new AntPropertyImpl(parent, tag, checksumDef, TOTAL_PROPERTY);
            }
            return new AntPropertyImpl(parent, tag, checksumDef, AntFileImpl.PROPERTY);
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(ExecTask.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            final AntTypeDefinition execDef = parent.getAntFile().getBaseTypeDefinition(ExecTask.class.getName());
            if (tag.getAttributeValue(RESULT_PROPERTY) != null) {
              return new AntPropertyImpl(parent, tag, execDef, RESULT_PROPERTY);
            }
            return new AntPropertyImpl(parent, tag, execDef, "outputproperty");
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(Java.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            final AntTypeDefinition execDef = parent.getAntFile().getBaseTypeDefinition(Java.class.getName());
            return new AntPropertyImpl(parent, tag, execDef, RESULT_PROPERTY);
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(Input.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            final AntTypeDefinition execDef = parent.getAntFile().getBaseTypeDefinition(Input.class.getName());
            return new AntPropertyImpl(parent, tag, execDef, "addproperty");
          }
        });
        /*
        ourAntTypeToKnownAntElementCreatorMap.put(Exit.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            final AntTypeDefinition failTaskDefinition = parent.getAntFile().getBaseTypeDefinition(Exit.class.getName());
            if (tag.getAttributeValue(AntFileImpl.IF_ATTR) != null) {
              return new AntPropertyImpl(parent, tag, failTaskDefinition, AntFileImpl.IF_ATTR);
            }
            return new AntPropertyImpl(parent, tag, failTaskDefinition, AntFileImpl.UNLESS_ATTR);
          }
        });
        */
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
        ourAntTypeToKnownAntElementCreatorMap.put(ScriptDef.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntScriptDefImpl((AntStructuredElement)parent, tag,
                                        parent.getAntFile().getBaseTypeDefinition(ScriptDef.class.getName()));
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
        ourAntTypeToKnownAntElementCreatorMap.put(BuildNumber.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntBuildNumberImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(BuildNumber.class.getName()));
          }
        });

        ourAntTypeToKnownAntElementCreatorMap.put(DirSet.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntDirSetImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(DirSet.class.getName()));
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(FileList.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntFileListImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(FileList.class.getName()));
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(FileSet.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntFileSetImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(FileSet.class.getName()));
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(Path.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntPathImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Path.class.getName()));
          }
        });
        ourAntTypeToKnownAntElementCreatorMap.put(Path.PathElement.class.getName(), new AntElementCreator() {
          public AntStructuredElement create(final AntElement parent, final XmlTag tag) {
            return new AntPathImpl(parent, tag, parent.getAntFile().getBaseTypeDefinition(Path.PathElement.class.getName()));
          }
        });
        
      }
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
    Length.class.getName(), WhichResource.class.getName(), JarLibResolveTask.class.getName(), P4Counter.class.getName(), ManifestClassPath.class.getName()};

  private final static String[] ALL_TASKS_CONTAINER_CLASSES =
    {MacroDef.NestedSequential.class.getName(), Sequential.class.getName(), Parallel.class.getName()};

  private static interface AntElementCreator {
    AntStructuredElement create(final AntElement parent, final XmlTag tag);
  }
}
