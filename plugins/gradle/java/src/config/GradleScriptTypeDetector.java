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
package org.jetbrains.plugins.gradle.config;

import com.intellij.internal.statistic.collectors.fus.fileTypes.FileTypeUsageSchemaDescriptor;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.gradle.util.GradleConstants;
import org.jetbrains.plugins.groovy.GroovyFileType;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author sergey.evdokimov
 */
public class GradleScriptTypeDetector extends GroovyScriptTypeDetector implements FileTypeUsageSchemaDescriptor {
  public GradleScriptTypeDetector() {
    super(GradleScriptType.INSTANCE, GradleConstants.EXTENSION);
  }

  @Override
  public boolean isSpecificScriptFile(@NotNull GroovyFile script) {
    return GradleConstants.EXTENSION.equals(script.getViewProvider().getVirtualFile().getExtension());
  }

  @Override
  public boolean describes(@NotNull VirtualFile file) {
    String name = file.getName();
    return file.getFileType() == GroovyFileType.GROOVY_FILE_TYPE &&
           (name.equals(GradleConstants.DEFAULT_SCRIPT_NAME) || name.equals(GradleConstants.SETTINGS_FILE_NAME));
  }
}
