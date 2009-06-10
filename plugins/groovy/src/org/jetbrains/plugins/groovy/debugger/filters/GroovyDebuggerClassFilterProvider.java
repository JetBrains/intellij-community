package org.jetbrains.plugins.groovy.debugger.filters;

import com.intellij.ui.classFilter.ClassFilter;
import com.intellij.ui.classFilter.DebuggerClassFilterProvider;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author ilyas
 */
public class GroovyDebuggerClassFilterProvider implements DebuggerClassFilterProvider {

  @NonNls private static final String[] PROHIBITED_CLASS_PATTERNS =
    {"org.codehaus.groovy.*",
     "groovy.lang.Meta*", "groovy.lang.GroovyObjectSupport", "groovy.lang.GroovySystem", "groovy.lang.Binding", "groovy.lang.GroovyShell", "groovy.lang.Script*",
     "groovy.ui.GroovyMain",
     "groovy.lang.MissingPropertyException", "groovy.lang.GroovyRuntimeException"};

  private static final ClassFilter[] FITERS = ContainerUtil.map(PROHIBITED_CLASS_PATTERNS, new Function<String, ClassFilter>() {
    public ClassFilter fun(final String s) {
      return new ClassFilter(s);
    }
  }, new ClassFilter[0]);

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
