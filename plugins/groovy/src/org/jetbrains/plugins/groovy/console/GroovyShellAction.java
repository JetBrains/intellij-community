/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.console;

import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;

/**
 * @author peter
 */
public class GroovyShellAction extends GroovyShellActionBase {
  @Override
  protected boolean isSuitableModule(Module module) {
    return super.isSuitableModule(module) && DefaultGroovyShellRunner.hasGroovyWithNeededJars(module);
  }

  @Override
  protected GroovyShellRunner getRunner(Module module) {
    return new DefaultGroovyShellRunner();
  }

  @Override
  public String getTitle() {
    return "Groovy Shell";
  }

  @Override
  protected GroovyShellConsoleImpl createConsole(Project project, String title) {
    return new GroovyShellConsoleImpl(project, title);
  }
}
