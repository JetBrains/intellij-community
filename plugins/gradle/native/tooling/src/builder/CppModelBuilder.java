// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.gradle.nativeplatform.tooling.builder;

import org.gradle.api.Project;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.file.*;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTree;
import org.gradle.api.internal.project.DefaultProject;
import org.gradle.api.plugins.PluginContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.language.cpp.CppBinary;
import org.gradle.language.cpp.CppComponent;
import org.gradle.language.cpp.CppSharedLibrary;
import org.gradle.language.cpp.CppStaticLibrary;
import org.gradle.language.cpp.plugins.CppBasePlugin;
import org.gradle.language.cpp.plugins.CppPlugin;
import org.gradle.language.cpp.tasks.CppCompile;
import org.gradle.language.nativeplatform.ComponentWithExecutable;
import org.gradle.language.nativeplatform.internal.ConfigurableComponentWithExecutable;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingSchemeFactory;
import org.gradle.nativeplatform.tasks.LinkExecutable;
import org.gradle.nativeplatform.toolchain.*;
import org.gradle.nativeplatform.toolchain.internal.NativeLanguageTools;
import org.gradle.nativeplatform.toolchain.internal.PlatformToolProvider;
import org.gradle.nativeplatform.toolchain.internal.ToolType;
import org.gradle.nativeplatform.toolchain.internal.tools.CommandLineToolSearchResult;
import org.gradle.nativeplatform.toolchain.internal.tools.ToolSearchPath;
import org.gradle.platform.base.internal.toolchain.ToolSearchResult;
import org.gradle.util.GradleVersion;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.gradle.model.FilePatternSetImpl;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CompilerDetails;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppBinary.TargetType;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppFileSettings;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.CppProject;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.LinkerDetails;
import org.jetbrains.plugins.gradle.nativeplatform.tooling.model.impl.*;
import org.jetbrains.plugins.gradle.tooling.ErrorMessageBuilder;
import org.jetbrains.plugins.gradle.tooling.ModelBuilderService;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

/**
 * The prototype of the C++ project gradle tooling model builder.
 * This implementation should be moved or replaced with the similar model builder from the Gradle distribution.
 *
 * @author Vladislav.Soroka
 */
public class CppModelBuilder implements ModelBuilderService {
  private static final boolean IS_48_OR_BETTER = GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.8")) >= 0;
  private static final boolean IS_47_OR_BETTER = IS_48_OR_BETTER ||
                                                 GradleVersion.current().getBaseVersion().compareTo(GradleVersion.version("4.7")) >= 0;

  @Override
  public boolean canBuild(String modelName) {
    return CppProject.class.getName().equals(modelName);
  }

  @Nullable
  @Override
  public Object buildAll(final String modelName, final Project project) {
    PluginContainer pluginContainer = project.getPlugins();
    if (!pluginContainer.hasPlugin(CppBasePlugin.class)) {
      if (pluginContainer.hasPlugin(CppPlugin.class)) {
        project.getLogger().error(
          "[sync warning] The IDE doesn't support 'cpp' gradle plugin. " +
          "Consider to use new gradle C++ plugins, see details at https://blog.gradle.org/introducing-the-new-cpp-plugins");
      }
      return null;
    }

    final CppProjectImpl cppProject = new CppProjectImpl();
    for (SoftwareComponent component : project.getComponents()) {
      if (component instanceof CppComponent) {
        File cppCompilerExecutable = null;
        CppComponent cppComponent = (CppComponent)component;
        for (CppBinary cppBinary : cppComponent.getBinaries().get()) {
          if (cppCompilerExecutable == null) {
            cppCompilerExecutable = findCppCompilerExecutable(project, cppBinary);
          }

          List<String> compilerArgs = new ArrayList<String>();
          Set<File> compileIncludePath = new LinkedHashSet<File>(cppBinary.getCompileIncludePath().getFiles());

          Map<File, CppFileSettings> sources = new HashMap<File, CppFileSettings>();
          for (File file : cppBinary.getCppSource().getFiles()) {
            sources.put(file, new CppFileSettingsImpl());
          }

          String baseName = cppBinary.getBaseName().getOrElse("");
          String variantName = StringUtils.removeStart(cppBinary.getName(), "main");
          String compileTaskName = null;
          Set<File> systemIncludes = new LinkedHashSet<File>();
          Provider<CppCompile> compileTask = cppBinary.getCompileTask();
          if (compileTask.isPresent()) {
            CppCompile cppCompile = compileTask.get();
            compileTaskName = cppCompile.getPath();
            compilerArgs.addAll(cppCompile.getCompilerArgs().getOrElse(Collections.<String>emptyList()));

            //Since Gradle 4.8, system header include directories should be accessed separately via the systemIncludes property
            //see https://github.com/gradle/gradle-native/blob/master/docs/RELEASE-NOTES.md#better-control-over-system-include-path-for-native-compilation---583
            if (IS_48_OR_BETTER) {
              compileIncludePath.addAll(cppCompile.getIncludes().getFiles());
              systemIncludes.addAll(cppCompile.getSystemIncludes().getFiles());
            }
            else {
              systemIncludes.addAll(cppCompile.getIncludes().getFiles());
            }

            appendFileSettings(sources, project, cppBinary, cppCompile);
          }

          File executableFile = null;
          String linkTaskName = null;
          boolean isExecutable = cppBinary instanceof ComponentWithExecutable;
          if (isExecutable) {
            Provider<? extends LinkExecutable> fileProvider = ((ComponentWithExecutable)cppBinary).getLinkTask();
            if (fileProvider.isPresent()) {
              LinkExecutable linkExecutable = fileProvider.get();
              linkTaskName = linkExecutable.getPath();
              executableFile = getExecutableFile(linkExecutable);
            }
          }

          TargetType targetType = null;
          if (isExecutable) {
            targetType = TargetType.EXECUTABLE;
          }
          else if (cppBinary instanceof CppSharedLibrary) {
            targetType = TargetType.SHARED_LIBRARY;
          }
          else if (cppBinary instanceof CppStaticLibrary) {
            targetType = TargetType.STATIC_LIBRARY;
          }

          // resolve compiler working dir as compiler executable file parent dir
          // https://github.com/gradle/gradle/blob/7422d5fc2e04d564dfd73bc539a37b62f8e2113a/subprojects/platform-native/src/main/java/org/gradle/nativeplatform/toolchain/internal/metadata/AbstractMetadataProvider.java#L61
          File compilerWorkingDir = cppCompilerExecutable == null ? null : cppCompilerExecutable.getParentFile();
          String compileKind = getCompilerKind(cppBinary);
          CompilerDetails compilerDetails = new CompilerDetailsImpl(
            compileKind, compileTaskName, cppCompilerExecutable, compilerWorkingDir, compilerArgs, compileIncludePath, systemIncludes);
          LinkerDetails linkerDetails = new LinkerDetailsImpl(linkTaskName, executableFile);
          cppProject.addBinary(new CppBinaryImpl(baseName, variantName, sources, compilerDetails, linkerDetails, targetType));
        }

        addSourceFolders(cppProject, cppComponent);
      }
    }

    return cppProject;
  }

  private static void appendFileSettings(Map<File, CppFileSettings> sources,
                                         Project project,
                                         CppBinary cppBinary,
                                         CppCompile cppCompile) {

    if (cppCompile.getObjectFileDir().isPresent()) {
      File objectFileDir = cppCompile.getObjectFileDir().get().getAsFile();
      Iterator<CompilerOutputFileNamingSchemeFactory> it =
        ((DefaultProject)project).getServices().getAll(CompilerOutputFileNamingSchemeFactory.class).iterator();
      if (it.hasNext()) {
        CompilerOutputFileNamingSchemeFactory outputFileNamingSchemeFactory = it.next();
        String objectFileExtension = cppBinary.getTargetPlatform().getOperatingSystem().isWindows() ? ".obj" : ".o";
        CompilerOutputFileNamingScheme outputFileNamingScheme = outputFileNamingSchemeFactory.create();
        outputFileNamingScheme.withOutputBaseFolder(objectFileDir).withObjectFileNameSuffix(objectFileExtension);

        for (Map.Entry<File, CppFileSettings> fileSettingsEntry : sources.entrySet()) {
          File objectFile = outputFileNamingScheme.map(fileSettingsEntry.getKey());
          ((CppFileSettingsImpl)fileSettingsEntry.getValue()).setObjectFile(objectFile);
        }
      }
    }
  }

  @NotNull
  private static String getCompilerKind(CppBinary cppBinary) {
    final String compileKind;
    NativeToolChain toolChain = cppBinary.getToolChain();
    if (toolChain instanceof Clang) {
      compileKind = "Clang";
    }
    else if (toolChain instanceof Gcc) {
      compileKind = "GCC";
    }
    else if (toolChain instanceof VisualCpp) {
      compileKind = "MSVC";
    }
    else if (toolChain instanceof Swiftc) {
      compileKind = "Swiftc";
    }
    else {
      compileKind = "Unknown";
    }
    return compileKind;
  }

  @Nullable
  private static File getExecutableFile(LinkExecutable linkExecutable) {
    File executableFile;
    RegularFileProperty binaryFile = null;
    if (IS_47_OR_BETTER) {
      binaryFile = linkExecutable.getLinkedFile();
    }
    else {
      try {
        Object linkedFile = linkExecutable.getClass().getMethod("getBinaryFile").invoke(linkExecutable);
        if (linkedFile instanceof RegularFileProperty) {
          binaryFile = (RegularFileProperty)linkedFile;
        }
      }
      catch (Exception e) {
        //noinspection CallToPrintStackTrace
        e.printStackTrace();
      }
    }
    executableFile = binaryFile != null ? binaryFile.getAsFile().getOrNull() : null;
    return executableFile;
  }

  private static void addSourceFolders(final CppProjectImpl cppProject, CppComponent cppComponent) {
    for (File dir : cppComponent.getPrivateHeaderDirs()) {
      cppProject.addSourceFolder(
        new SourceFolderImpl(dir, new FilePatternSetImpl(Collections.<String>emptySet(), Collections.<String>emptySet())));
    }

    FileCollection cppSource = cppComponent.getCppSource();
    // try to resolve cpp source folders
    ((FileCollectionInternal)cppSource).visitRootElements(new FileCollectionVisitor() {
      @Override
      public void visitCollection(FileCollectionInternal internal) {
      }

      @Override
      public void visitTree(FileTreeInternal internal) {
      }

      @Override
      public void visitDirectoryTree(DirectoryFileTree tree) {
        File dir = tree.getDir();
        PatternSet patterns = tree.getPatterns();
        cppProject.addSourceFolder(new SourceFolderImpl(dir, new FilePatternSetImpl(patterns.getIncludes(), patterns.getExcludes())));
      }
    });
  }

  @Nullable
  private static File findCppCompilerExecutable(Project project, CppBinary cppBinary) {
    Throwable throwable = null;
    try {
      if (cppBinary instanceof ConfigurableComponentWithExecutable) {
        PlatformToolProvider toolProvider = ((ConfigurableComponentWithExecutable)cppBinary).getPlatformToolProvider();
        ToolSearchResult toolSearchResult = toolProvider.isToolAvailable(ToolType.CPP_COMPILER);
        if (toolSearchResult.isAvailable()) {
          if (toolSearchResult instanceof CommandLineToolSearchResult) {
            return ((CommandLineToolSearchResult)toolSearchResult).getTool();
          }
          // dirty hack because of dummy implementation of org.gradle.nativeplatform.toolchain.internal.msvcpp.VisualCppPlatformToolProvider.isToolAvailable
          if (toolProvider.getClass().getSimpleName().equals("VisualCppPlatformToolProvider")) {
            Field visualCppField = toolProvider.getClass().getDeclaredField("visualCpp");
            visualCppField.setAccessible(true);
            Object visualCpp = visualCppField.get(toolProvider);

            if (visualCpp instanceof NativeLanguageTools) {
              return ((NativeLanguageTools)visualCpp).getCompilerExecutable();
            }
          }
        }
      }
    }
    catch (Throwable t) {
      throwable = t;
    }

    NativeToolChain toolChain = cppBinary.getToolChain();
    String exeName;
    if (toolChain instanceof Gcc) {
      exeName = "g++";
    }
    else if (toolChain instanceof Clang) {
      exeName = "clang++";
    }
    else if (toolChain instanceof VisualCpp) {
      exeName = "cl";
    }
    else {
      exeName = null;
    }

    if (exeName != null) {
      ToolSearchPath toolSearchPath = new ToolSearchPath(OperatingSystem.current());
      CommandLineToolSearchResult searchResult = toolSearchPath.locate(ToolType.CPP_COMPILER, exeName);
      if (searchResult.isAvailable()) {
        return searchResult.getTool();
      }
    }

    if (!IS_47_OR_BETTER) {
      project.getLogger().error(
        "[sync error] Unable to resolve compiler executable. " +
        "The project uses '" + GradleVersion.current() + "' try to update the gradle version");
    }
    else {
      project.getLogger().error("[sync error] Unable to resolve compiler executable", throwable);
    }
    return null;
  }

  @NotNull
  @Override
  public ErrorMessageBuilder getErrorMessageBuilder(@NotNull Project project, @NotNull Exception e) {
    return ErrorMessageBuilder.create(
      project, e, "C++ project import errors"
    ).withDescription("Unable to import C++ project");
  }
}
