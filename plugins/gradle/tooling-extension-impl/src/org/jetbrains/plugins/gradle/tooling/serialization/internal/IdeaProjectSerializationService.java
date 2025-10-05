// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gradle.tooling.serialization.internal;

import com.amazon.ion.IonReader;
import com.amazon.ion.IonType;
import com.amazon.ion.IonWriter;
import com.amazon.ion.system.IonReaderBuilder;
import org.gradle.api.JavaVersion;
import gnu.trove.TObjectHashingStrategy;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;
import org.gradle.tooling.internal.consumer.converters.BackwardsCompatibleIdeaModuleDependency;
import org.gradle.tooling.model.*;
import org.gradle.tooling.model.idea.*;
import org.gradle.tooling.model.java.InstalledJdk;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.tooling.serialization.SerializationService;
import org.jetbrains.plugins.gradle.tooling.serialization.internal.adapter.*;
import org.jetbrains.plugins.gradle.tooling.util.GradleContainerUtil;
import org.jetbrains.plugins.gradle.tooling.util.GradleVersionComparator;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap;
import org.jetbrains.plugins.gradle.tooling.util.IntObjectMap.SimpleObjectFactory;
import org.jetbrains.plugins.gradle.tooling.util.ObjectCollector;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static com.intellij.openapi.util.Comparing.compare;
import static org.jetbrains.plugins.gradle.tooling.serialization.ToolingStreamApiUtils.*;

/**
 * @author Vladislav.Soroka
 */
public final class IdeaProjectSerializationService implements SerializationService<IdeaProject> {
  private final WriteContext myWriteContext;
  private final ReadContext myReadContext;

  public IdeaProjectSerializationService() {
    myWriteContext = new WriteContext();
    myReadContext = new ReadContext();
  }

  @Override
  public byte[] write(IdeaProject ideaProject, Class<? extends IdeaProject> modelClazz) throws IOException {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (IonWriter writer = createIonWriter().build(out)) {
      GradleVersion gradleVersion = getBuildGradleVersion(ideaProject);
      myWriteContext.setGradleVersion(gradleVersion);
      writer.stepIn(IonType.STRUCT);
      writeString(writer, "gradleVersion", gradleVersion.getVersion());
      writer.stepOut();
      writeProject(writer, myWriteContext, ideaProject);
    }
    return out.toByteArray();
  }

  @Override
  public IdeaProject read(byte[] object, Class<? extends IdeaProject> modelClazz) throws IOException {
    try (IonReader reader = IonReaderBuilder.standard().build(object)) {
      if (reader.next() == null) return null;
      reader.stepIn();
      String gradleVersion = assertNotNull(readString(reader, "gradleVersion"));
      reader.stepOut();
      myReadContext.setGradleVersion(GradleVersion.version(gradleVersion));
      return readProject(reader, myReadContext);
    }
  }

  @Override
  public Class<? extends IdeaProject> getModelClass() {
    return IdeaProject.class;
  }

  private static void writeProject(final IonWriter writer,
                                   final WriteContext context,
                                   final IdeaProject project) throws IOException {
    context.ideaProjectsCollector.add(project, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "name", project.getName());
          writeString(writer, "description", project.getDescription());
          writeString(writer, "jdkName", project.getJdkName());
          writeJavaLanguageSettings(writer, context, project.getJavaLanguageSettings());
          writeString(writer, "languageLevel", project.getLanguageLevel().getLevel());
          writeModules(writer, context, project.getModules());
        }
        writer.stepOut();
      }
    });
  }


  private static void writeModules(IonWriter writer, WriteContext context, DomainObjectSet<? extends IdeaModule> ideaModules)
    throws IOException {
    writer.setFieldName("ideaModules");
    writer.stepIn(IonType.LIST);
    for (IdeaModule ideaModule : ideaModules) {
      writeModule(writer, context, ideaModule);
    }
    writer.stepOut();
  }

  private static void writeModule(final IonWriter writer, final WriteContext context, final IdeaModule ideaModule) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeString(writer, "name", ideaModule.getName());
    writeString(writer, "description", ideaModule.getDescription());
    writeString(writer, "jdkName", nullizeUnsupported(new Supplier<String>() {
      @Override
      public String get() {
        return ideaModule.getJdkName();
      }
    }));
    writeGradleProject(writer, "gradleProject", context, ideaModule.getGradleProject());
    writeCompilerOutput(writer, context, ideaModule.getCompilerOutput());
    writeContentRoots(writer, ideaModule.getContentRoots());
    writeJavaLanguageSettings(writer, context, nullizeUnsupported(new Supplier<IdeaJavaLanguageSettings>() {
      @Override
      public IdeaJavaLanguageSettings get() {
        return ideaModule.getJavaLanguageSettings();
      }
    }));
    writeDependencies(writer, context, ideaModule.getDependencies());
    writer.stepOut();
  }

  private static void writeDependencies(IonWriter writer,
                                        WriteContext context,
                                        DomainObjectSet<? extends IdeaDependency> dependencies) throws IOException {
    writer.setFieldName("dependencies");
    writer.stepIn(IonType.LIST);
    for (IdeaDependency dependency : dependencies) {
      writeDependency(writer, context, dependency);
    }
    writer.stepOut();
  }

  private static void writeDependency(IonWriter writer, WriteContext context, IdeaDependency dependency) throws IOException {
    if (dependency instanceof IdeaModuleDependency) {
      writeModuleDependency(writer, context, (IdeaModuleDependency)dependency);
    }
    else if (dependency instanceof IdeaSingleEntryLibraryDependency) {
      writeLibraryDependency(writer, context, (IdeaSingleEntryLibraryDependency)dependency);
    }
    else {
      Object unpack = context.modelAdapter.unpack(dependency);
      if (unpack.getClass().getSimpleName().equals("DefaultIdeaModuleDependency")) {
        BackwardsCompatibleIdeaModuleDependency moduleDependency =
          context.modelAdapter.adapt(BackwardsCompatibleIdeaModuleDependency.class, unpack);
        writeModuleDependency(writer, context, moduleDependency);
      }
      else if (unpack.getClass().getSimpleName().equals("DefaultIdeaSingleEntryLibraryDependency")) {
        IdeaSingleEntryLibraryDependency libraryDependency = context.modelAdapter.adapt(IdeaSingleEntryLibraryDependency.class, unpack);
        writeLibraryDependency(writer, context, libraryDependency);
      }
    }
  }

  private static void writeLibraryDependency(final IonWriter writer, WriteContext context,
                                             final IdeaSingleEntryLibraryDependency libraryDependency)
    throws IOException {
    context.ideaDependenciesCollector.add(libraryDependency, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "_type", IdeaSingleEntryLibraryDependency.class.getSimpleName());
          writeFile(writer, "file", libraryDependency.getFile());
          writeFile(writer, "javadoc", libraryDependency.getJavadoc());
          writeFile(writer, "source", libraryDependency.getSource());
          writeString(writer, "scope", libraryDependency.getScope().getScope());
          writeBoolean(writer, "exported", libraryDependency.getExported());

          writer.setFieldName("gradleModuleVersion");
          GradleModuleVersion version = libraryDependency.getGradleModuleVersion();
          if (version != null) {
            writer.stepIn(IonType.STRUCT);
            writeString(writer, "group", version.getGroup());
            writeString(writer, "name", version.getName());
            writeString(writer, "version", version.getVersion());
            writer.stepOut();
          }
          else {
            writer.writeNull();
          }
        }
        writer.stepOut();
      }
    });
  }

  private static void writeModuleDependency(final IonWriter writer, final WriteContext context, final IdeaModuleDependency moduleDependency)
    throws IOException {
    context.ideaDependenciesCollector.add(moduleDependency, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "_type", IdeaModuleDependency.class.getSimpleName());
          writeString(writer, "targetModuleName", new TargetModuleNameGetter(moduleDependency, context.myGradleVersionComparator).get());
          writeString(writer, "scope", moduleDependency.getScope().getScope());
          writeBoolean(writer, "exported", moduleDependency.getExported());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeJavaLanguageSettings(final @NotNull IonWriter writer,
                                                final @NotNull WriteContext context,
                                                final @Nullable IdeaJavaLanguageSettings languageSettings) throws IOException {
    writer.setFieldName("javaLanguageSettings");
    if (languageSettings == null) {
      writer.writeNull();
      return;
    }
    context.ideaJavaLanguageSettingsCollector.add(languageSettings, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "languageLevel", getJavaVersion(getLanguageLevel(languageSettings)));
          writeString(writer, "targetBytecodeVersion",
                      getJavaVersion(nullizeUnsupported(new TargetBytecodeVersionGetter(languageSettings))));
          writer.setFieldName("jdk");
          InstalledJdk jdk = nullizeUnsupported(new JdkGetter(languageSettings));
          if (jdk != null) {
            writer.stepIn(IonType.STRUCT);
            writeFile(writer, "javaHome", jdk.getJavaHome());
            JavaVersion jdkJavaVersion = getJavaVersion(jdk);
            writeString(writer, "javaVersion", jdkJavaVersion == null ? null : jdkJavaVersion.name());
            writer.stepOut();
          }
          else {
            writer.writeNull();
          }
        }
        writer.stepOut();
      }
    });
  }

  private static void writeContentRoots(IonWriter writer, DomainObjectSet<? extends IdeaContentRoot> roots) throws IOException {
    writer.setFieldName("contentRoots");
    writer.stepIn(IonType.LIST);
    for (IdeaContentRoot contentRoot : roots) {
      writeContentRoot(writer, contentRoot);
    }
    writer.stepOut();
  }

  private static void writeContentRoot(IonWriter writer, IdeaContentRoot contentRoot) throws IOException {
    writer.stepIn(IonType.STRUCT);
    writeFile(writer, "rootDirectory", contentRoot.getRootDirectory());
    writeSourceDirectories(writer, "sourceDirectories", contentRoot.getSourceDirectories());
    writeSourceDirectories(writer, "testDirectories", contentRoot.getTestDirectories());
    writeSourceDirectories(writer, "resourceDirectories", notNullize(nullizeUnsupported(new ResourceDirectoriesGetter(contentRoot))));
    writeSourceDirectories(writer, "testResourceDirectories",
                           notNullize(nullizeUnsupported(new TestResourceDirectoriesGetter(contentRoot))));
    writeFiles(writer, "excludeDirectories", contentRoot.getExcludeDirectories());
    writer.stepOut();
  }

  private static void writeSourceDirectories(IonWriter writer, String fieldName, DomainObjectSet<? extends IdeaSourceDirectory> directories)
    throws IOException {
    writer.setFieldName(fieldName);
    writer.stepIn(IonType.LIST);
    for (IdeaSourceDirectory sourceDirectory : directories) {
      writer.stepIn(IonType.STRUCT);
      writeBoolean(writer, "generated", sourceDirectory.isGenerated());
      writeFile(writer, "directory", sourceDirectory.getDirectory());
      writer.stepOut();
    }
    writer.stepOut();
  }

  private static void writeCompilerOutput(final IonWriter writer,
                                          final WriteContext context,
                                          final IdeaCompilerOutput ideaCompilerOutput)
    throws IOException {
    writer.setFieldName("compilerOutput");
    if (ideaCompilerOutput == null) {
      writer.writeNull();
      return;
    }
    context.ideaCompilerOutputCollector.add(ideaCompilerOutput, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeBoolean(writer, "inheritOutputDirs", ideaCompilerOutput.getInheritOutputDirs());
          writeFile(writer, "outputDir", ideaCompilerOutput.getOutputDir());
          writeFile(writer, "testOutputDir", ideaCompilerOutput.getTestOutputDir());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeGradleProject(final @NotNull IonWriter writer,
                                         @Nullable String fieldName,
                                         final @NotNull WriteContext context,
                                         final @Nullable GradleProject gradleProject) throws IOException {
    if (fieldName != null) {
      writer.setFieldName(fieldName);
    }
    if (gradleProject == null) {
      writer.writeNull();
      return;
    }
    context.gradleProjectsCollector.add(gradleProject, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "name", gradleProject.getName());
          writeString(writer, "description", gradleProject.getDescription());
          writeFile(writer, "projectDirectory", gradleProject.getProjectDirectory());
          writeProjectIdentifier(writer, context, gradleProject.getProjectIdentifier());
          writeFile(writer, "buildDirectory", gradleProject.getBuildDirectory());
          writeGradleProject(writer, "parent", context, gradleProject.getParent());

          writer.setFieldName("children");
          writer.stepIn(IonType.LIST);
          for (GradleProject ideaModule : gradleProject.getChildren()) {
            writeGradleProject(writer, null, context, ideaModule);
          }
          writer.stepOut();

          writeFile(writer, "buildScript", gradleProject.getBuildScript().getSourceFile());

          writer.setFieldName("tasks");
          writer.stepIn(IonType.LIST);
          for (GradleTask task : gradleProject.getTasks()) {
            writeGradleTask(writer, context, task);
          }
          writer.stepOut();
        }
        writer.stepOut();
      }
    });
  }

  private static void writeGradleTask(final IonWriter writer,
                                      final WriteContext context,
                                      final @Nullable GradleTask task)
    throws IOException {
    if (task == null) {
      writer.writeNull();
      return;
    }
    context.gradleTasksCollector.add(task, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "name", task.getName());
          writeString(writer, "description", task.getDescription());
          writeString(writer, "path", task.getPath());
          writeString(writer, "group", task.getGroup());
          writeString(writer, "displayName", task.getDisplayName());
          writeBoolean(writer, "isPublic", task.isPublic());
          writeProjectIdentifier(writer, context, task.getProjectIdentifier());
          writeGradleProject(writer, "project", context, task.getProject());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeProjectIdentifier(final IonWriter writer,
                                             final WriteContext context,
                                             final ProjectIdentifier projectIdentifier) throws IOException {
    writer.setFieldName("projectIdentifier");
    if (projectIdentifier == null) {
      writer.writeNull();
      return;
    }
    context.projectIdentifiersCollector.add(projectIdentifier, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeString(writer, "projectPath", projectIdentifier.getProjectPath());
          writeBuildIdentifier(writer, context, projectIdentifier.getBuildIdentifier());
        }
        writer.stepOut();
      }
    });
  }

  private static void writeBuildIdentifier(final IonWriter writer,
                                           final WriteContext context,
                                           final BuildIdentifier buildIdentifier) throws IOException {
    writer.setFieldName("buildIdentifier");
    context.buildIdentifiersCollector.add(buildIdentifier, new ObjectCollector.Processor<IOException>() {
      @Override
      public void process(boolean isAdded, int objectId) throws IOException {
        writer.stepIn(IonType.STRUCT);
        writer.setFieldName(OBJECT_ID_FIELD);
        writer.writeInt(objectId);
        if (isAdded) {
          writeFile(writer, "rootDir", buildIdentifier.getRootDir());
        }
        writer.stepOut();
      }
    });
  }


  private @Nullable InternalIdeaProject readProject(final @NotNull IonReader reader,
                                                    final @NotNull ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    InternalIdeaProject project =
      context.projectsMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.ObjectFactory<InternalIdeaProject>() {
        @Override
        public InternalIdeaProject newInstance() {
          return new InternalIdeaProject();
        }

        @Override
        public void fill(InternalIdeaProject ideaProject) {
          ideaProject.setName(assertNotNull(readString(reader, "name")));
          ideaProject.setDescription(readString(reader, "description"));
          ideaProject.setJdkName(readString(reader, "jdkName"));
          ideaProject.setJavaLanguageSettings(readJavaLanguageSettings(reader, context));
          ideaProject.setLanguageLevel(new InternalIdeaLanguageLevel(readString(reader, "languageLevel")));
          readModules(reader, context, ideaProject);
        }
      });
    reader.stepOut();
    return project;
  }

  private void readModules(IonReader reader, ReadContext context, InternalIdeaProject project) {
    reader.next();
    assertFieldName(reader, "ideaModules");
    reader.stepIn();
    List<InternalIdeaModule> ideaModules = new ArrayList<>();
    InternalIdeaModule ideaModule;
    while ((ideaModule = readModule(reader, context)) != null) {
      ideaModule.setParent(project);
      ideaModules.add(ideaModule);
    }
    project.setModules(ideaModules);
    reader.stepOut();
  }

  private @Nullable InternalIdeaModule readModule(IonReader reader, ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();
    InternalIdeaModule ideaModule = new InternalIdeaModule();
    ideaModule.setName(readString(reader, "name"));
    ideaModule.setDescription(readString(reader, "description"));
    ideaModule.setJdkName(readString(reader, "jdkName"));
    ideaModule.setGradleProject(readGradleProject(reader, context, "gradleProject"));
    ideaModule.setCompilerOutput(readCompilerOutput(reader, context));
    ideaModule.setContentRoots(readContentRoots(reader));
    ideaModule.setJavaLanguageSettings(readJavaLanguageSettings(reader, context));
    ideaModule.setDependencies(readDependencies(reader, context));
    reader.stepOut();
    return ideaModule;
  }

  private static List<InternalIdeaDependency> readDependencies(IonReader reader, ReadContext context) {
    reader.next();
    assertFieldName(reader, "dependencies");
    reader.stepIn();
    List<InternalIdeaDependency> dependencies = new ArrayList<>();
    InternalIdeaDependency dependency;
    while ((dependency = readDependency(reader, context)) != null) {
      dependencies.add(dependency);
    }
    reader.stepOut();
    return dependencies;
  }

  private static InternalIdeaDependency readDependency(final IonReader reader, final ReadContext context) {
    if (reader.next() == null) return null;
    reader.stepIn();

    InternalIdeaDependency dependency =
      context.dependenciesMap
        .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.SimpleObjectFactory<InternalIdeaDependency>() {
        @Override
        public InternalIdeaDependency create() {
          String type = readString(reader, "_type");
          if (IdeaModuleDependency.class.getSimpleName().equals(type)) {
            InternalIdeaModuleDependency moduleDependency = new InternalIdeaModuleDependency();
            moduleDependency.setTargetModuleName(readString(reader, "targetModuleName"));
            moduleDependency.setScope(InternalIdeaDependencyScope.getInstance(readString(reader, "scope")));
            moduleDependency.setExported(readBoolean(reader, "exported"));
            return moduleDependency;
          }
          else if (IdeaSingleEntryLibraryDependency.class.getSimpleName().equals(type)) {
            File file = assertNotNull(readFile(reader, "file"));
            InternalIdeaSingleEntryLibraryDependency libraryDependency = new InternalIdeaSingleEntryLibraryDependency(file);
            libraryDependency.setJavadoc(readFile(reader, "javadoc"));
            libraryDependency.setSource(readFile(reader, "source"));
            libraryDependency.setScope(InternalIdeaDependencyScope.getInstance(readString(reader, "scope")));
            libraryDependency.setExported(readBoolean(reader, "exported"));

            IonType ionType = reader.next();
            assertFieldName(reader, "gradleModuleVersion");
            if (ionType != IonType.NULL) {
              reader.stepIn();
              InternalGradleModuleVersion moduleVersion = new InternalGradleModuleVersion();
              moduleVersion.setGroup(readString(reader, "group"));
              moduleVersion.setName(readString(reader, "name"));
              moduleVersion.setVersion(readString(reader, "version"));
              reader.stepOut();
              libraryDependency.setModuleVersion(moduleVersion);
            }
            return libraryDependency;
          }
          else {
            throw new RuntimeException("Unsupported dependency '" + type + "'");
          }
        }
      });
    reader.stepOut();
    return dependency;
  }

  private @NotNull List<InternalIdeaContentRoot> readContentRoots(IonReader reader) {
    reader.next();
    assertFieldName(reader, "contentRoots");
    reader.stepIn();
    List<InternalIdeaContentRoot> contentRoots = new ArrayList<>();
    InternalIdeaContentRoot child;
    while ((child = readContentRoot(reader)) != null) {
      contentRoots.add(child);
    }
    reader.stepOut();
    return contentRoots;
  }

  private InternalIdeaContentRoot readContentRoot(IonReader reader) {
    if (reader.next() == null) return null;
    reader.stepIn();
    InternalIdeaContentRoot contentRoot = new InternalIdeaContentRoot(myReadContext.myGradleVersionComparator);
    contentRoot.setRootDirectory(readFile(reader, "rootDirectory"));
    contentRoot.setSourceDirectories(readSourceDirectories(reader, "sourceDirectories"));
    contentRoot.setTestDirectories(readSourceDirectories(reader, "testDirectories"));
    contentRoot.setResourceDirectories(readSourceDirectories(reader, "resourceDirectories"));
    contentRoot.setTestResourceDirectories(readSourceDirectories(reader, "testResourceDirectories"));
    contentRoot.setExcludeDirectories(readFileSet(reader, "excludeDirectories"));
    reader.stepOut();
    return contentRoot;
  }

  private static Set<InternalIdeaSourceDirectory> readSourceDirectories(IonReader reader, String fieldName) {
    IonType ionType = reader.next();
    if (fieldName != null) {
      assertFieldName(reader, fieldName);
    }
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }

    reader.stepIn();
    Set<InternalIdeaSourceDirectory> sourceDirectories = new LinkedHashSet<>();
    InternalIdeaSourceDirectory sourceDirectory;
    while ((sourceDirectory = readSourceDirectory(reader)) != null) {
      sourceDirectories.add(sourceDirectory);
    }
    reader.stepOut();
    return sourceDirectories;
  }

  private static InternalIdeaSourceDirectory readSourceDirectory(IonReader reader) {
    IonType ionType = reader.next();
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }
    reader.stepIn();
    InternalIdeaSourceDirectory sourceDirectory = new InternalIdeaSourceDirectory();
    sourceDirectory.setGenerated(readBoolean(reader, "generated"));
    sourceDirectory.setDirectory(readFile(reader, "directory"));
    reader.stepOut();
    return sourceDirectory;
  }

  private static InternalIdeaCompilerOutput readCompilerOutput(final IonReader reader, final ReadContext context) {
    IonType ionType = reader.next();
    assertFieldName(reader, "compilerOutput");
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }

    reader.stepIn();
    InternalIdeaCompilerOutput project =
      context.compilerOutputsMap
        .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.SimpleObjectFactory<InternalIdeaCompilerOutput>() {
          @Override
          public InternalIdeaCompilerOutput create() {
            InternalIdeaCompilerOutput compilerOutput = new InternalIdeaCompilerOutput();
            compilerOutput.setInheritOutputDirs(readBoolean(reader, "inheritOutputDirs"));
            compilerOutput.setOutputDir(readFile(reader, "outputDir"));
            compilerOutput.setTestOutputDir(readFile(reader, "testOutputDir"));
            return compilerOutput;
          }
        });
    reader.stepOut();
    return project;
  }

  private static @Nullable InternalGradleProject readGradleProject(final IonReader reader, final ReadContext context, final String fieldName) {
    IonType ionType = reader.next();
    if (fieldName != null) {
      assertFieldName(reader, fieldName);
    }
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }
    reader.stepIn();
    InternalGradleProject project =
      context.gradleProjectsMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.ObjectFactory<InternalGradleProject>() {
        @Override
        public InternalGradleProject newInstance() {
          return new InternalGradleProject();
        }

        @Override
        public void fill(InternalGradleProject gradleProject) {
          gradleProject.setName(readString(reader, "name"));
          gradleProject.setDescription(readString(reader, "description"));
          gradleProject.setProjectDirectory(readFile(reader, "projectDirectory"));
          gradleProject.setProjectIdentifier(readProjectIdentifier(reader, context));
          gradleProject.setBuildDirectory(readFile(reader, "buildDirectory"));
          gradleProject.setParent(readGradleProject(reader, context, "parent"));

          reader.next();
          assertFieldName(reader, "children");
          reader.stepIn();
          List<InternalGradleProject> children = new ArrayList<>();
          InternalGradleProject child;
          while ((child = readGradleProject(reader, context, null)) != null) {
            children.add(child);
          }
          gradleProject.setChildren(children);
          reader.stepOut();

          gradleProject.getBuildScript().setSourceFile(readFile(reader, "buildScript"));

          reader.next();
          assertFieldName(reader, "tasks");
          reader.stepIn();
          List<InternalGradleTask> tasks = new ArrayList<>();
          InternalGradleTask task;
          while ((task = readGradleTask(reader, context)) != null) {
            tasks.add(task);
          }
          gradleProject.setTasks(tasks);
          reader.stepOut();
        }
      });
    reader.stepOut();
    return project;
  }

  private static InternalGradleTask readGradleTask(final IonReader reader, final ReadContext context) {
    IonType ionType = reader.next();
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }

    reader.stepIn();
    InternalGradleTask project =
      context.tasksMap.computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new IntObjectMap.ObjectFactory<InternalGradleTask>() {
        @Override
        public InternalGradleTask newInstance() {
          return new InternalGradleTask();
        }

        @Override
        public void fill(InternalGradleTask gradleTask) {
          gradleTask.setName(readString(reader, "name"));
          gradleTask.setDescription(readString(reader, "description"));
          gradleTask.setPath(readString(reader, "path"));
          gradleTask.setGroup(readString(reader, "group"));
          gradleTask.setDisplayName(readString(reader, "displayName"));
          gradleTask.setPublic(readBoolean(reader, "isPublic"));
          gradleTask.setProjectIdentifier(readProjectIdentifier(reader, context));
          gradleTask.setGradleProject(readGradleProject(reader, context, "project"));
        }
      });
    reader.stepOut();
    return project;
  }

  private static InternalProjectIdentifier readProjectIdentifier(final IonReader reader, final ReadContext context) {
    IonType ionType = reader.next();
    assertFieldName(reader, "projectIdentifier");
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }

    reader.stepIn();
    InternalProjectIdentifier dependency = context.projectsIdentifiersMap
      .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<InternalProjectIdentifier>() {
        @Override
        public InternalProjectIdentifier create() {
          String projectPath = readString(reader, "projectPath");
          InternalBuildIdentifier buildIdentifier = readBuildIdentifier(reader, context);
          return new InternalProjectIdentifier(buildIdentifier, projectPath);
        }
      });
    reader.stepOut();
    return dependency;
  }

  private static InternalBuildIdentifier readBuildIdentifier(final IonReader reader, final ReadContext context) {
    IonType ionType = reader.next();
    assertFieldName(reader, "buildIdentifier");
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }

    reader.stepIn();
    InternalBuildIdentifier buildIdentifier = context.buildsIdentifiersMap
      .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<InternalBuildIdentifier>() {
        @Override
        public InternalBuildIdentifier create() {
          return new InternalBuildIdentifier(assertNotNull(readFile(reader, "rootDir")));
        }
      });
    reader.stepOut();
    return buildIdentifier;
  }

  private static InternalIdeaJavaLanguageSettings readJavaLanguageSettings(final IonReader reader, ReadContext context) {
    IonType ionType = reader.next();
    assertFieldName(reader, "javaLanguageSettings");
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }
    reader.stepIn();

    InternalIdeaJavaLanguageSettings dependency = context.languageSettingsMap
      .computeIfAbsent(readInt(reader, OBJECT_ID_FIELD), new SimpleObjectFactory<InternalIdeaJavaLanguageSettings>() {
        @Override
        public InternalIdeaJavaLanguageSettings create() {
          InternalIdeaJavaLanguageSettings languageSettings = new InternalIdeaJavaLanguageSettings();
          String languageLevel = readString(reader, "languageLevel");
          if (languageLevel != null) {
            languageSettings.setLanguageLevel(JavaVersion.valueOf(languageLevel));
          }
          String bytecodeVersion = readString(reader, "targetBytecodeVersion");
          if (bytecodeVersion != null) {
            languageSettings.setTargetBytecodeVersion(JavaVersion.valueOf(bytecodeVersion));
          }
          languageSettings.setJdk(readJdk(reader));
          return languageSettings;
        }
      });
    reader.stepOut();
    return dependency;
  }

  private static @Nullable InternalInstalledJdk readJdk(IonReader reader) {
    IonType ionType = reader.next();
    assertFieldName(reader, "jdk");
    if (ionType == IonType.NULL || ionType == null) {
      return null;
    }

    reader.stepIn();
    File javaHome = readFile(reader, "javaHome");
    String versionString = readString(reader, "javaVersion");
    JavaVersion javaVersion = versionString == null ? null : JavaVersion.valueOf(versionString);
    InternalInstalledJdk jdk = new InternalInstalledJdk(javaHome, javaVersion);
    reader.stepOut();
    return jdk;
  }

  private static class ReadContext {
    private final IntObjectMap<InternalIdeaProject> projectsMap = new IntObjectMap<>();
    private final IntObjectMap<InternalIdeaJavaLanguageSettings> languageSettingsMap = new IntObjectMap<>();
    private final IntObjectMap<InternalGradleProject> gradleProjectsMap = new IntObjectMap<>();
    private final IntObjectMap<InternalProjectIdentifier> projectsIdentifiersMap = new IntObjectMap<>();
    private final IntObjectMap<InternalBuildIdentifier> buildsIdentifiersMap = new IntObjectMap<>();
    private final IntObjectMap<InternalGradleTask> tasksMap = new IntObjectMap<>();
    private final IntObjectMap<InternalIdeaCompilerOutput> compilerOutputsMap = new IntObjectMap<>();
    private final IntObjectMap<InternalIdeaDependency> dependenciesMap = new IntObjectMap<>();

    private GradleVersionComparator myGradleVersionComparator;

    private void setGradleVersion(@NotNull GradleVersion gradleVersion) {
      myGradleVersionComparator = new GradleVersionComparator(gradleVersion);
    }
  }

  private static final class WriteContext {
    private final ProtocolToModelAdapter modelAdapter = new ProtocolToModelAdapter();
    private GradleVersionComparator myGradleVersionComparator;

    private void setGradleVersion(@NotNull GradleVersion gradleVersion) {
      myGradleVersionComparator = new GradleVersionComparator(gradleVersion);
    }

    private final ObjectCollector<IdeaProject, IOException> ideaProjectsCollector = new ObjectCollector<>(
      new TObjectHashingStrategy<IdeaProject>() {
        @Override
        public int computeHashCode(IdeaProject object) {
          return object == null ? 0 : object.getName().hashCode();
        }

        @Override
        public boolean equals(IdeaProject o1, IdeaProject o2) {
          if (o1 == o2) return true;
          if (o1 != null && o2 != null) {
            DomainObjectSet<? extends IdeaModule> modules1 = o1.getModules();
            DomainObjectSet<? extends IdeaModule> modules2 = o2.getModules();
            if (modules1.size() != modules2.size()) return false;

            BuildIdentifier buildIdentifier1 = modules1.getAt(0).getGradleProject().getProjectIdentifier().getBuildIdentifier();
            BuildIdentifier buildIdentifier2 = modules2.getAt(0).getGradleProject().getProjectIdentifier().getBuildIdentifier();
            return isSameBuild(buildIdentifier1, buildIdentifier2);
          }
          return false;
        }
      });

    private final ObjectCollector<GradleProject, IOException> gradleProjectsCollector = new ObjectCollector<>(
      new TObjectHashingStrategy<GradleProject>() {
        @Override
        public int computeHashCode(GradleProject object) {
          return object == null ? 0 : object.getPath().hashCode();
        }

        @Override
        public boolean equals(GradleProject o1, GradleProject o2) {
          return o1 == o2 || o1 != null && o2 != null &&
                             isSameProject(o1.getProjectIdentifier(), o2.getProjectIdentifier());
        }
      });

    private final ObjectCollector<IdeaCompilerOutput, IOException> ideaCompilerOutputCollector =
      new ObjectCollector<>(
        new TObjectHashingStrategy<IdeaCompilerOutput>() {
          @Override
          public int computeHashCode(IdeaCompilerOutput object) {
            return argsHashCode(object.getInheritOutputDirs(), object.getOutputDir(), object.getTestOutputDir());
          }

          @Override
          public boolean equals(IdeaCompilerOutput o1, IdeaCompilerOutput o2) {
            return o1 == o2 || o1 != null && o2 != null &&
                               o1.getInheritOutputDirs() == o2.getInheritOutputDirs() &&
                               compare(o1.getOutputDir(), o2.getOutputDir(), new FilePathComparator()) == 0 &&
                               compare(o1.getTestOutputDir(), o2.getTestOutputDir()) == 0;
          }
        });

    private final ObjectCollector<GradleTask, IOException> gradleTasksCollector = new ObjectCollector<>(
      new TObjectHashingStrategy<GradleTask>() {
        @Override
        public int computeHashCode(GradleTask object) {
          return object == null ? 0 : object.getPath().hashCode();
        }

        @Override
        public boolean equals(GradleTask o1, GradleTask o2) {
          return o1 == o2 || o1 != null && o2 != null &&
                             o1.getPath().equals(o2.getPath()) &&
                             isSameProject(o1.getProjectIdentifier(), o2.getProjectIdentifier());
        }
      });

    private static boolean isSameBuild(BuildIdentifier buildIdentifier1, BuildIdentifier buildIdentifier2) {
      String rootBuildPath1 = buildIdentifier1.getRootDir().getPath();
      String rootBuildPath2 = buildIdentifier2.getRootDir().getPath();
      return rootBuildPath1.equals(rootBuildPath2);
    }

    private final ObjectCollector<IdeaDependency, IOException> ideaDependenciesCollector =
      new ObjectCollector<>(
        new TObjectHashingStrategy<IdeaDependency>() {
          @Override
          public int computeHashCode(IdeaDependency object) {
            if (object == null) return 0;
            if (object instanceof IdeaModuleDependency) return computeHashCode((IdeaModuleDependency)object);
            if (object instanceof IdeaSingleEntryLibraryDependency) return computeHashCode((IdeaSingleEntryLibraryDependency)object);
            return object.hashCode();
          }

          @Override
          public boolean equals(IdeaDependency o1, IdeaDependency o2) {
            if (o1 == o2) return true;
            if (o1 != null && o2 != null) {
              if (o1 instanceof IdeaModuleDependency && o2 instanceof IdeaModuleDependency) {
                return equals((IdeaModuleDependency)o1, (IdeaModuleDependency)o2);
              }
              if (o1 instanceof IdeaSingleEntryLibraryDependency && o2 instanceof IdeaSingleEntryLibraryDependency) {
                return equals((IdeaSingleEntryLibraryDependency)o1, (IdeaSingleEntryLibraryDependency)o2);
              }
            }
            return false;
          }

          private int computeHashCode(final @NotNull IdeaModuleDependency object) {
            return argsHashCode(new TargetModuleNameGetter(object, myGradleVersionComparator).get(), object.getScope().getScope());
          }

          private int computeHashCode(@NotNull IdeaSingleEntryLibraryDependency object) {
            return argsHashCode(object.getFile(), object.getScope().getScope(), hasCode(object.getGradleModuleVersion()));
          }

          private int hasCode(@Nullable GradleModuleVersion version) {
            return version == null ? 0 : argsHashCode(version.getGroup(), version.getName(), version.getVersion());
          }

          private boolean equals(@NotNull IdeaModuleDependency o1, @NotNull IdeaModuleDependency o2) {
            return o1.getExported() == o2.getExported() &&
                   nullsafeEqual(new TargetModuleNameGetter(o1, myGradleVersionComparator).get(),
                                 new TargetModuleNameGetter(o2, myGradleVersionComparator).get()) &&
                   nullsafeEqual(o1.getScope().getScope(), o2.getScope().getScope());
          }

          private boolean equals(@NotNull IdeaSingleEntryLibraryDependency o1, @NotNull IdeaSingleEntryLibraryDependency o2) {
            return o1.getExported() == o2.getExported() &&
                   equal(o1.getGradleModuleVersion(), o2.getGradleModuleVersion()) &&
                   nullsafeEqual(o1.getFile().getPath(), o2.getFile().getPath()) &&
                   nullsafeEqual(o1.getScope().getScope(), o2.getScope().getScope());
          }

          private boolean equal(@Nullable GradleModuleVersion version1, @Nullable GradleModuleVersion version2) {
            return version1 == version2 || version1 != null && version2 != null &&
                                           nullsafeEqual(version1.getName(), version2.getName()) &&
                                           nullsafeEqual(version1.getGroup(), version2.getGroup()) &&
                                           nullsafeEqual(version1.getVersion(), version2.getVersion());
          }
        });

    private final ObjectCollector<IdeaJavaLanguageSettings, IOException> ideaJavaLanguageSettingsCollector =
      new ObjectCollector<>(
        new TObjectHashingStrategy<IdeaJavaLanguageSettings>() {
          @Override
          public int computeHashCode(final IdeaJavaLanguageSettings object) {
            return object == null ? 0 : argsHashCode(getLanguageLevel(object),
                                                         nullizeUnsupported(new TargetBytecodeVersionGetter(object)),
                                                         nullizeUnsupported(new JavaHomePathGetter(object)));
          }

          @Override
          public boolean equals(IdeaJavaLanguageSettings o1, IdeaJavaLanguageSettings o2) {
            return o1 == o2 ||
                   o1 != null && o2 != null &&
                   getLanguageLevel(o1) == getLanguageLevel(o2) &&
                   nullsafeEqual(nullizeUnsupported(new TargetBytecodeVersionGetter(o1)),
                                 nullizeUnsupported(new TargetBytecodeVersionGetter(o2))) &&
                   nullsafeEqual(nullizeUnsupported(new JavaHomePathGetter(o1)), nullizeUnsupported(new JavaHomePathGetter(o2)));
          }
        });

    private final ObjectCollector<ProjectIdentifier, IOException> projectIdentifiersCollector =
      new ObjectCollector<>(
        new TObjectHashingStrategy<ProjectIdentifier>() {
          @Override
          public int computeHashCode(ProjectIdentifier object) {
            return object == null ? 0 : object.getProjectPath().hashCode();
          }

          @Override
          public boolean equals(ProjectIdentifier o1, ProjectIdentifier o2) {
            return o1 == o2 || o1 != null && o2 != null && isSameProject(o1, o2);
          }
        });

    private static boolean isSameProject(ProjectIdentifier o1, ProjectIdentifier o2) {
      if (o1 == null || o2 == null) return false;
      if (!o1.getProjectPath().equals(o2.getProjectPath())) return false;
      return isSameBuild(o1.getBuildIdentifier(), o2.getBuildIdentifier());
    }

    private final ObjectCollector<BuildIdentifier, IOException> buildIdentifiersCollector =
      new ObjectCollector<>(
        new TObjectHashingStrategy<BuildIdentifier>() {
          @Override
          public int computeHashCode(BuildIdentifier object) {
            return object == null ? 0 : object.getRootDir().getPath().hashCode();
          }

          @Override
          public boolean equals(BuildIdentifier o1, BuildIdentifier o2) {
            return o1 == o2 || o1 != null && o2 != null && isSameBuild(o1, o2);
          }
        });

    private static class FilePathComparator implements Comparator<File> {
      @Override
      public int compare(File o1, File o2) {
        return o1.getPath().compareTo(o2.getPath());
      }
    }
  }

  private static @Nullable String getJavaVersion(@Nullable JavaVersion javaVersion) {
    return javaVersion == null ? null : javaVersion.name();
  }

  private static @Nullable JavaVersion getLanguageLevel(@NotNull IdeaJavaLanguageSettings languageSettings) {
    try {
      return languageSettings.getLanguageLevel();
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static @Nullable JavaVersion getTargetBytecodeVersion(@NotNull IdeaJavaLanguageSettings languageSettings) {
    try {
      return languageSettings.getTargetBytecodeVersion();
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static @Nullable JavaVersion getJavaVersion(@NotNull InstalledJdk jdk) {
    try {
      return jdk.getJavaVersion();
    }
    catch (IllegalArgumentException e) {
      return null;
    }
  }

  private static final class TargetBytecodeVersionGetter implements Supplier<JavaVersion> {
    private final IdeaJavaLanguageSettings myObject;

    private TargetBytecodeVersionGetter(IdeaJavaLanguageSettings object) {myObject = object;}

    @Override
    public JavaVersion get() {
      return getTargetBytecodeVersion(myObject);
    }
  }

  private static final class JdkGetter implements Supplier<InstalledJdk> {
    private final IdeaJavaLanguageSettings myObject;

    private JdkGetter(IdeaJavaLanguageSettings object) {myObject = object;}

    @Override
    public InstalledJdk get() {
      return myObject.getJdk();
    }
  }

  private static final class JavaHomePathGetter implements Supplier<String> {
    private final IdeaJavaLanguageSettings myObject;

    private JavaHomePathGetter(IdeaJavaLanguageSettings object) {myObject = object;}

    @Override
    public String get() {
      InstalledJdk jdk = myObject.getJdk();
      if (jdk != null) {
        File home = jdk.getJavaHome();
        return home == null ? null : home.getPath();
      }
      return null;
    }
  }

  private static @Nullable <T> T nullizeUnsupported(@NotNull Supplier<T> getter) {
    try {
      return getter.get();
    }
    catch (UnsupportedMethodException e) {
      return null;
    }
  }

  public static @NotNull <T> DomainObjectSet<T> notNullize(@Nullable DomainObjectSet<T> set) {
    return set == null ? GradleContainerUtil.emptyDomainObjectSet() : set;
  }

  private static final class ResourceDirectoriesGetter implements Supplier<DomainObjectSet<? extends IdeaSourceDirectory>> {
    private final IdeaContentRoot myContentRoot;

    private ResourceDirectoriesGetter(IdeaContentRoot contentRoot) {myContentRoot = contentRoot;}

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> get() {
      return myContentRoot.getResourceDirectories();
    }
  }

  private static final class TestResourceDirectoriesGetter implements Supplier<DomainObjectSet<? extends IdeaSourceDirectory>> {
    private final IdeaContentRoot myContentRoot;

    private TestResourceDirectoriesGetter(IdeaContentRoot contentRoot) {myContentRoot = contentRoot;}

    @Override
    public DomainObjectSet<? extends IdeaSourceDirectory> get() {
      return myContentRoot.getTestResourceDirectories();
    }
  }

  private static final class TargetModuleNameGetter implements Supplier<String> {
    private final IdeaModuleDependency myModuleDependency;
    private final GradleVersionComparator myGradleVersionComparator;

    private TargetModuleNameGetter(@NotNull IdeaModuleDependency moduleDependency,
                                   @NotNull GradleVersionComparator gradleVersionComparator) {
      myModuleDependency = moduleDependency;
      myGradleVersionComparator = gradleVersionComparator;
    }

    @Override
    public String get() {
      if (myModuleDependency instanceof BackwardsCompatibleIdeaModuleDependency && myGradleVersionComparator.lessThan("3.1")) {
        return ((BackwardsCompatibleIdeaModuleDependency)myModuleDependency).getDependencyModule().getName();
      }
      else {
        return myModuleDependency.getTargetModuleName();
      }
    }
  }

  private static @NotNull GradleVersion getBuildGradleVersion(@Nullable IdeaProject ideaProject) {
    try {
      ClassLoader classLoader = new ProtocolToModelAdapter().unpack(ideaProject).getClass().getClassLoader();
      Class<?> gradleVersionClass = classLoader.loadClass("org.gradle.util.GradleVersion");
      Object buildGradleVersion = gradleVersionClass.getMethod("current").invoke(gradleVersionClass);
      return GradleVersion.version(gradleVersionClass.getMethod("getVersion").invoke(buildGradleVersion).toString());
    }
    catch (Exception ignore) {
    }
    return GradleVersion.current();
  }

  private static int argsHashCode(Object... objects) {
    return Arrays.hashCode(objects);
  }

  private static boolean nullsafeEqual(Object a, Object b) {
    return a == b || a != null && a.equals(b);
  }


}

