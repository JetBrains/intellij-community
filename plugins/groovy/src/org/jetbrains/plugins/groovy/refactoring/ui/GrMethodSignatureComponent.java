/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.refactoring.ui;

import com.intellij.openapi.project.Project;
import com.intellij.refactoring.ui.MethodSignatureComponent;
import org.jetbrains.plugins.groovy.GroovyFileType;

/**
 * @author Max Medvedev
 */
public class GrMethodSignatureComponent extends MethodSignatureComponent {
  private final String myFileName;

  public GrMethodSignatureComponent(String signature, Project project) {
    super(signature, project, GroovyFileType.GROOVY_FILE_TYPE);
    myFileName = "dummy." + GroovyFileType.GROOVY_FILE_TYPE.getDefaultExtension();
  }

  @Override
  protected String getFileName() {
    return myFileName;
  }
}
