// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.ant;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.PatternSetRef;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.fileTypes.FileTypeRegistry;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Couple;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.runner.GroovyScriptUtil;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.Collection;
import java.util.Collections;

/**
 * Groovy provider for custom compilation task
 */
public class GroovyAntCustomCompilerProvider extends ChunkCustomCompilerExtension {
  /**
   * The property for groovyc task SDK
   */
  @NonNls private static final String GROOVYC_TASK_SDK_PROPERTY = "grooovyc.task.sdk";

  /**
   * {@inheritDoc}
   */
  @Override
  public void generateCustomCompile(Project project,
                                    ModuleChunk chunk,
                                    GenerationOptions genOptions,
                                    boolean compileTests,
                                    CompositeGenerator generator,
                                    Tag compilerArgs,
                                    Tag bootClassPathTag,
                                    Tag classpathTag,
                                    PatternSetRef compilerExcludes,
                                    Tag srcTag,
                                    String outputPathRef) {
    Tag groovyc = new Tag("groovyc", Couple.of("destdir", outputPathRef), Couple.of("fork", "yes"));
    // note that boot classpath tag is ignored
    groovyc.add(srcTag);
    groovyc.add(classpathTag);
    groovyc.add(compilerExcludes);
    final Tag javac =
      new Tag("javac", Couple.of("debug", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_DEBUG_INFO)));
    javac.add(compilerArgs);
    groovyc.add(javac);
    generator.add(groovyc);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public void generateCustomCompilerTaskRegistration(Project project, GenerationOptions genOptions, CompositeGenerator generator) {
    final GroovyConfigUtils utils = GroovyConfigUtils.getInstance();
    // find SDK library with maximum version number in order to use for compiler
    final Library[] libraries = utils.getAllUsedSDKLibraries(project);

    if (libraries.length == 0) {
      // no SDKs in the project, the task registration is not generated
      return;
    }
    final Collection<String> versions = utils.getSDKVersions(libraries);
    String maxVersion = versions.isEmpty() ? null : Collections.max(versions);
    Library sdkLib = null;
    for (Library lib : libraries) {
      if (maxVersion == null || maxVersion.equals(utils.getSDKVersion(LibrariesUtil.getGroovyLibraryHome(lib)))) {
        sdkLib = lib;
      }
    }
    assert sdkLib != null;
    String groovySdkPathRef = BuildProperties.getLibraryPathId(sdkLib.getName());
    generator.add(new Property(GROOVYC_TASK_SDK_PROPERTY, groovySdkPathRef));
    //noinspection HardCodedStringLiteral
    Tag taskdef = new Tag("taskdef", Couple.of("name", "groovyc"), Couple.of("classname", "org.codehaus.groovy.ant.Groovyc"),
                          Couple.of("classpathref", "${" + GROOVYC_TASK_SDK_PROPERTY + "}"));
    generator.add(taskdef);
  }

  /**
   * {@inheritDoc}
   */
  @Override
  public boolean hasCustomCompile(ModuleChunk chunk) {
    final Module[] modules = chunk.getModules();
    if (modules.length == 0) {
      return false;
    }
    final PsiManager manager = PsiManager.getInstance(chunk.getProject());
    final ContentIterator groovyFileSearcher = fileOrDir -> {
      ProgressManager.checkCanceled();
      return !isCompilableGroovyFile(manager, fileOrDir);
    };
    for (Module m : modules) {
      if (LibrariesUtil.hasGroovySdk(m)) {
        final ModuleRootManager rootManager = ModuleRootManager.getInstance(m);
        final ModuleFileIndex fileIndex = rootManager.getFileIndex();
        for (VirtualFile file : rootManager.getSourceRoots(JavaModuleSourceRootTypes.SOURCES)) {
          if (!fileIndex.iterateContentUnderDirectory(file, groovyFileSearcher)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * @return {@code true} if the file is in Groovy and it doesn't have custom script type
   */
  private static boolean isCompilableGroovyFile(PsiManager manager, VirtualFile file) {
    if (file.isDirectory()) {
      return false;
    }
    if (!FileTypeRegistry.getInstance().isFileOfType(file, GroovyFileType.GROOVY_FILE_TYPE)) {
      return false;
    }
    PsiFile psiFile = manager.findFile(file);
    if (!(psiFile instanceof GroovyFile)) {
      return false;
    }
    return GroovyScriptUtil.isPlainGroovyScript((GroovyFile)psiFile);
  }
}
