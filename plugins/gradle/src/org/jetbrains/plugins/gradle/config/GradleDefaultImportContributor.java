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

  @Override
  public List<String> appendImplicitlyImportedPackages(@NotNull GroovyFile file) {
    if (file.isScript() && GroovyScriptTypeDetector.getScriptType(file) instanceof GradleScriptType) {
      return Arrays.asList(
        "org.gradle",
        "org.gradle.util",
        "org.gradle.api",
        "org.gradle.api.artifacts",
        "org.gradle.api.artifacts.dsl",
        "org.gradle.api.artifacts.specs",
        "org.gradle.api.dependencies",
        "org.gradle.api.execution",
        "org.gradle.api.file",
        "org.gradle.api.logging",
        "org.gradle.api.initialization",
        "org.gradle.api.invocation",
        "org.gradle.api.plugins",
        "org.gradle.api.plugins.quality",
        "org.gradle.api.specs",
        "org.gradle.api.tasks",
        "org.gradle.api.tasks.bundling",
        "org.gradle.api.tasks.compile",
        "org.gradle.api.tasks.javadoc",
        "org.gradle.api.tasks.testing",
        "org.gradle.api.tasks.util",
        "org.gradle.api.tasks.wrapper"
      );
    }
    return Collections.emptyList();
  }
}
