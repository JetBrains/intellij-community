package org.jetbrains.plugins.groovy.ant;

import com.intellij.compiler.ant.*;
import com.intellij.compiler.ant.taskdefs.PatternSetRef;
import com.intellij.compiler.ant.taskdefs.Property;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.libraries.Library;
import com.intellij.openapi.util.Pair;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.plugins.groovy.config.GroovyConfigUtils;
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
      if (maxVersion == null || maxVersion.equals(utils.getSDKVersion(lib))) {
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
        return true;
      }
    }
    return false;
  }
}
