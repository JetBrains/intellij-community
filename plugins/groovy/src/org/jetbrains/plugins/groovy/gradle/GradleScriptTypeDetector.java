/*
 * Copyright 2000-2010 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.gradle;

import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

import java.util.regex.Pattern;

/**
 * @author sergey.evdokimov
 */
public class GradleScriptTypeDetector extends GroovyScriptTypeDetector {

  @NonNls private static final String GRADLE_EXTENSION = "gradle";

  public GradleScriptTypeDetector() {
    super(GradleScriptType.INSTANCE, GRADLE_EXTENSION);
  }

  @Override
  public boolean isSpecificScriptFile(@NotNull GroovyFile script) {
    return GRADLE_EXTENSION.equals(script.getViewProvider().getVirtualFile().getExtension());
  }
}
