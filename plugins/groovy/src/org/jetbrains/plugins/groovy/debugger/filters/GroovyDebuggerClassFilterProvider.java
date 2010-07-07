/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.execution.filters.StackFrameFilter;
import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;
import com.intellij.util.containers.ContainerUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyDebuggerClassFilterProvider extends StackFrameFilter implements DebuggerClassFilterProvider {
  private static final ClassFilter[] FILTERS = {new ClassFilter("org.codehaus.groovy.*"), new ClassFilter("groovy.*")};

  public List<ClassFilter> getFilters() {

    final GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    final ArrayList<ClassFilter> list = new ArrayList<ClassFilter>();
    if (flag == null || flag.booleanValue()) {
      ContainerUtil.addAll(list, FILTERS);
      return list;
    }
    return list;
  }

  public boolean isAuxiliaryFrame(String className, String methodName) {
    if (className.equals("org.codehaus.groovy.runtime.DefaultGroovyMethods") ||
        className.equals("org.codehaus.groovy.runtime.DefaultGroovyMethodsSupport")) {
      return false;
    }

    for (ClassFilter filter : FILTERS) {
      final String pattern = filter.getPattern();
      if (className.startsWith(pattern.substring(0, pattern.length() - 1))) {
        return true;
      }
    }
    return false;
  }
}
