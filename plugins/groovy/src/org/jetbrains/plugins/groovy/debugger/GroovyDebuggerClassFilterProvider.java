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
package org.jetbrains.plugins.groovy.debugger;

import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyDebuggerClassFilterProvider implements DebuggerClassFilterProvider {
  private static final List<ClassFilter> FILTERS = Arrays.asList(new ClassFilter("org.codehaus.groovy.*"), new ClassFilter("groovy.*"));

  @Override
  public List<ClassFilter> getFilters() {
    GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();
    Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    if (flag == null || flag.booleanValue()) {
      return FILTERS;
    }
    return Collections.emptyList();
  }

}
