package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyDebuggerClassFilterProvider implements DebuggerClassFilterProvider {
  private static final ClassFilter[] FITERS = {new ClassFilter("org.codehaus.groovy.*"), new ClassFilter("groovy.*")};

  public List<ClassFilter> getFilters() {

    final GroovyDebuggerSettings settings = GroovyDebuggerSettings.getInstance();
    final Boolean flag = settings.DEBUG_DISABLE_SPECIFIC_GROOVY_METHODS;
    final ArrayList<ClassFilter> list = new ArrayList<ClassFilter>();
    if (flag == null || flag.booleanValue()) {
      list.addAll(Arrays.asList(FITERS));
      return list;
    }
    return list;
  }

}
