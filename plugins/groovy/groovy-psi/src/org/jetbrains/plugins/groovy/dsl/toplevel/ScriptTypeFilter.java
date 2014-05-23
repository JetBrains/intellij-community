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
package org.jetbrains.plugins.groovy.dsl.toplevel;

import com.intellij.psi.PsiFile;
import com.intellij.util.ProcessingContext;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.groovy.dsl.GroovyClassDescriptor;
import org.jetbrains.plugins.groovy.extensions.GroovyScriptTypeDetector;
import org.jetbrains.plugins.groovy.lang.psi.GroovyFile;

/**
 * @author peter
 */
public class ScriptTypeFilter implements ContextFilter {
  private final String myScriptType;

  public ScriptTypeFilter(String scriptType) {
    myScriptType = scriptType;
  }

  @Override
  public boolean isApplicable(GroovyClassDescriptor descriptor, ProcessingContext ctx) {
    PsiFile file = descriptor.getPlaceFile();
    if (file instanceof GroovyFile && ((GroovyFile)file).isScript()) {
      return myScriptType.contains(getScriptTypeId((GroovyFile)file));
    }
    return false;
  }


  private static String getScriptTypeId(@NotNull GroovyFile script) {
    for (GroovyScriptTypeDetector detector : GroovyScriptTypeDetector.EP_NAME.getExtensions()) {
      if (detector.isSpecificScriptFile(script)) {
        return detector.getScriptType().getId();
      }
    }

    return "default";
  }
}
