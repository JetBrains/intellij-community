/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.config;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;
import org.jetbrains.plugins.groovy.lang.resolve.DefaultImportContributor;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author peter
 */
public class GradleDefaultImportContributor extends DefaultImportContributor {

  // As listed here - http://www.gradle.org/docs/current/userguide/userguide_single.html#sec:using_gradle_without_ide_support
  public static final String[] IMPLICIT_GRADLE_PACKAGES = {
    "org.gradle",
    "org.gradle.api",
    "org.gradle.api.artifacts",
    "org.gradle.api.artifacts.cache",
    "org.gradle.api.artifacts.dsl",
    "org.gradle.api.artifacts.maven",
    "org.gradle.api.artifacts.repositories",
    "org.gradle.api.artifacts.result",
    "org.gradle.api.component",
    "org.gradle.api.distribution",
    "org.gradle.api.distribution.plugins",
    "org.gradle.api.dsl",
    "org.gradle.api.execution",
    "org.gradle.api.file",
    "org.gradle.api.initialization",
    "org.gradle.api.initialization.dsl",
    "org.gradle.api.invocation",
    "org.gradle.api.java.archives",
    "org.gradle.api.logging",
    "org.gradle.api.plugins",
    "org.gradle.api.plugins.announce",
    "org.gradle.api.plugins.antlr",
    "org.gradle.api.plugins.buildcomparison.gradle",
    "org.gradle.api.plugins.jetty",
    "org.gradle.api.plugins.osgi",
    "org.gradle.api.plugins.quality",
    "org.gradle.api.plugins.scala",
    "org.gradle.api.plugins.sonar",
    "org.gradle.api.plugins.sonar.model",
    "org.gradle.api.publish",
    "org.gradle.api.publish.ivy",
    "org.gradle.api.publish.ivy.plugins",
    "org.gradle.api.publish.ivy.tasks",
    "org.gradle.api.publish.maven",
    "org.gradle.api.publish.maven.plugins",
    "org.gradle.api.publish.maven.tasks",
    "org.gradle.api.publish.plugins",
    "org.gradle.api.reporting",
    "org.gradle.api.reporting.plugins",
    "org.gradle.api.resources",
    "org.gradle.api.sonar.runner",
    "org.gradle.api.specs",
    "org.gradle.api.tasks",
    "org.gradle.api.tasks.ant",
    "org.gradle.api.tasks.application",
    "org.gradle.api.tasks.bundling",
    "org.gradle.api.tasks.compile",
    "org.gradle.api.tasks.diagnostics",
    "org.gradle.api.tasks.incremental",
    "org.gradle.api.tasks.javadoc",
    "org.gradle.api.tasks.scala",
    "org.gradle.api.tasks.testing",
    "org.gradle.api.tasks.testing.junit",
    "org.gradle.api.tasks.testing.testng",
    "org.gradle.api.tasks.util",
    "org.gradle.api.tasks.wrapper",
    "org.gradle.buildsetup.plugins",
    "org.gradle.buildsetup.tasks",
    "org.gradle.external.javadoc",
    "org.gradle.language.base",
    "org.gradle.language.base.plugins",
    "org.gradle.language.java",
    "org.gradle.language.jvm",
    "org.gradle.language.jvm.plugins",
    "org.gradle.language.jvm.tasks",
    "org.gradle.nativecode.base",
    "org.gradle.nativecode.base.plugins",
    "org.gradle.nativecode.base.tasks",
    "org.gradle.nativecode.cdt",
    "org.gradle.nativecode.cdt.tasks",
    "org.gradle.nativecode.language.cpp",
    "org.gradle.nativecode.language.cpp.plugins",
    "org.gradle.nativecode.language.cpp.tasks",
    "org.gradle.nativecode.toolchain.plugins",
    "org.gradle.plugins.binaries",
    "org.gradle.plugins.cpp",
    "org.gradle.plugins.cpp.cdt",
    "org.gradle.plugins.cpp.cdt.tasks",
    "org.gradle.plugins.cpp.compiler.capability",
    "org.gradle.plugins.cpp.gpp",
    "org.gradle.plugins.cpp.msvcpp",
    "org.gradle.plugins.ear",
    "org.gradle.plugins.ear.descriptor",
    "org.gradle.plugins.ide.api",
    "org.gradle.plugins.ide.eclipse",
    "org.gradle.plugins.ide.idea",
    "org.gradle.plugins.javascript.base",
    "org.gradle.plugins.javascript.coffeescript",
    "org.gradle.plugins.javascript.envjs",
    "org.gradle.plugins.javascript.envjs.browser",
    "org.gradle.plugins.javascript.envjs.http",
    "org.gradle.plugins.javascript.envjs.http.simple",
    "org.gradle.plugins.javascript.jshint",
    "org.gradle.plugins.javascript.rhino",
    "org.gradle.plugins.javascript.rhino.worker",
    "org.gradle.plugins.signing",
    "org.gradle.plugins.signing.signatory",
    "org.gradle.plugins.signing.signatory.pgp",
    "org.gradle.plugins.signing.type",
    "org.gradle.plugins.signing.type.pgp",
    "org.gradle.process",
    "org.gradle.testing.jacoco.plugins",
    "org.gradle.testing.jacoco.tasks",
    "org.gradle.util"
  };


  @Override
  public List<String> appendImplicitlyImportedPackages(@NotNull GroovyFile file) {
    if (file.isScript() && GroovyScriptTypeDetector.getScriptType(file) instanceof GradleScriptType) {
      return Arrays.asList(IMPLICIT_GRADLE_PACKAGES);
    }
    return Collections.emptyList();
  }
}
