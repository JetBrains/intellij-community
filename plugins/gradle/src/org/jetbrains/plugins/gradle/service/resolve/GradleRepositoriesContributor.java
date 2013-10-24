/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.gradle.service.resolve;

/**
 * @author Denis Zhdanov
 * @since 8/14/13 12:56 PM
 */
public class GradleRepositoriesContributor extends GradleSimpleContributor {
  public GradleRepositoriesContributor() {
    super("repositories", GradleCommonClassNames.GRADLE_API_REPOSITORY_HANDLER,
          GradleCommonClassNames.GRADLE_API_PLUGINS_MAVEN_REPOSITORY_HANDLER_CONVENTION);
  }
}
