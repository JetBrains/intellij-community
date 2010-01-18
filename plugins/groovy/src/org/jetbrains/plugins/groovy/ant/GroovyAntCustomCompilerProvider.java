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
package org.jetbrains.plugins.groovy.ant;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.PatternSetRef;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ContentIterator;
import com.intellij.openapi.roots.ModuleFileIndex;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.GroovyFileTypeLoader;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
import org.jetbrains.plugins.groovy.util.LibrariesUtil;

import java.util.Collection;
import java.util.Collections;
import java.util.Set;

/**
 * Groovy provider for custom compilation task
 */
public class GroovyAntCustomCompilerProvider extends ChunkCustomCompilerExtension {
  /**
   * The property for groovyc task SDK
   */
  @NonNls private final static String GROOVYC_TASK_SDK_PROPERTY = "grooovyc.task.sdk";

  /**
   * {@inheritDoc}
   */
  public void generateCustomCompile(Project project,
                                    ModuleChunk chunk,
                                    GenerationOptions genOptions,
                                    boolean compileTests,
                                    CompositeGenerator generator,
                                    Tag compilerArgs,
                                    Tag bootclasspathTag,
                                    Tag classpathTag,
                                    PatternSetRef compilerExcludes,
                                    Tag srcTag,
                                    String outputPathRef) {
    Tag groovyc = new Tag("groovyc", Pair.create("destdir", outputPathRef), Pair.create("fork", "yes"));
    // note that boot classpath tag is ignored
    groovyc.add(srcTag);
    groovyc.add(classpathTag);
    groovyc.add(compilerExcludes);
    final Tag javac =
      new Tag("javac", Pair.create("debug", BuildProperties.propertyRef(BuildProperties.PROPERTY_COMPILER_GENERATE_DEBUG_INFO)));
    javac.add(compilerArgs);
    groovyc.add(javac);
    generator.add(groovyc);
  }

  /**
   * {@inheritDoc}
   */
  public void generateCustomCompilerTaskRegistration(Project project, GenerationOptions genOptions, CompositeGenerator generator) {
    final GroovyConfigUtils utils = GroovyConfigUtils.getInstance();
    // find SDK library with maximum version number in order to use for compiler
    final Library[] libraries = utils.getAllSDKLibraries(project);
    if (libraries.length == 0) {
      // no SDKs in the project, the task registration is not generated
      return;
    }
    final Collection<String> versions = utils.getSDKVersions(project);
    String maxVersion = versions.isEmpty() ? null : Collections.max(versions);
    Library sdkLib = null;
    for (Library lib : libraries) {
      if (maxVersion == null || maxVersion.equals(utils.getSDKVersion(LibrariesUtil.getGroovyLibraryHome(lib)))) {
        sdkLib = lib;
      }
    }
    assert sdkLib != null;
    String grovySdkPathRef = BuildProperties.getLibraryPathId(sdkLib.getName());
    generator.add(new Property(GROOVYC_TASK_SDK_PROPERTY, grovySdkPathRef));
    //noinspection HardCodedStringLiteral
    Tag taskdef = new Tag("taskdef", Pair.create("name", "groovyc"), Pair.create("classname", "org.codehaus.groovy.ant.Groovyc"),
                          Pair.create("classpathref", "${" + GROOVYC_TASK_SDK_PROPERTY + "}"));
    generator.add(taskdef);
  }

  /**
   * {@inheritDoc}
   */
  public boolean hasCustomCompile(ModuleChunk chunk) {
    for (Module m : chunk.getModules()) {
      if (LibrariesUtil.hasGroovySdk(m)) {
        final Set<String> scriptExtensions = GroovyFileTypeLoader.getCustomGroovyScriptExtensions();
        final ContentIterator groovyFileSearcher = new ContentIterator() {
          public boolean processFile(VirtualFile fileOrDir) {
            ProgressManager.checkCanceled();
            if (isCompilableGroovyFile(fileOrDir, scriptExtensions)) {
              return false;
            }
            return true;
          }
        };

        final ModuleRootManager rootManager = ModuleRootManager.getInstance(m);
        final ModuleFileIndex fileIndex = rootManager.getFileIndex();
        for (VirtualFile file : rootManager.getSourceRoots()) {
          if (!fileIndex.iterateContentUnderDirectory(file, groovyFileSearcher)) {
            return true;
          }
        }
      }
    }
    return false;
  }

  private static boolean isCompilableGroovyFile(VirtualFile file, Set<String> scriptExtensions) {
    return !file.isDirectory() && GroovyFileType.GROOVY_FILE_TYPE == file.getFileType() && !scriptExtensions.contains(file.getExtension());
  }
}
