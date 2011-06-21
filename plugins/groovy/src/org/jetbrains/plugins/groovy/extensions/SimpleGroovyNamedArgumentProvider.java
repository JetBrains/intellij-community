package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.*;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Sergey Evdokimov
 */
public class SimpleGroovyNamedArgumentProvider {

  public static final ExtensionPointName<SimpleGroovyNamedArgumentProvider> EP_NAME =
    new ExtensionPointName<SimpleGroovyNamedArgumentProvider>("org.intellij.groovy.namedArguments");

  @Attribute("class")
  public String className;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public MethodDescriptor[] methods;

  private static Map<String, Map<String, Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor>>> MAP;

  public static Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> getMethodMap(@Nullable String className,
                                                                                         @Nullable String methodName) {
    if (MAP == null) {
      Map<String, Map<String, Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor>>> map =
        new HashMap<String, Map<String, Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor>>>();

      for (SimpleGroovyNamedArgumentProvider provider : EP_NAME.getExtensions()) {
        assert !StringUtil.isEmptyOrSpaces(provider.className);

        Map<String, Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor>> methodMap = map.get(provider.className);
        if (methodMap == null) {
          methodMap = new HashMap<String, Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor>>();
          map.put(provider.className, methodMap);
        }

        for (MethodDescriptor methodDescriptor : provider.methods) {
          assert !StringUtil.isEmptyOrSpaces(methodDescriptor.name);

          Object oldValue = methodMap.put(methodDescriptor.name, methodDescriptor.getArgumentsMap());
          assert oldValue == null;
        }
      }

      MAP = map;
    }

    Map<String, Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor>> methodMap = MAP.get(className);
    if (methodMap == null) return null;

    return methodMap.get(methodName);
  }

  @Tag("method")
  public static class MethodDescriptor {
    @Attribute("name")
    public String name;

    @Attribute("showFirst")
    public Boolean isFirst;

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public Argument[] arguments;

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    public Arguments[] argumentLists;

    public Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> getArgumentsMap() {
      Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> res =
        new HashMap<String, GroovyNamedArgumentProvider.ArgumentDescriptor>();

      if (arguments != null) {
        for (Argument argument : arguments) {
          String name = argument.name.trim();
          assert name.length() > 0;

          Object oldValue = res.put(name, getDescriptor(argument.isFirst, isFirst, argument.type));
          assert oldValue == null;
        }
      }

      if (argumentLists != null) {
        for (Arguments arguments : argumentLists) {
          GroovyNamedArgumentProvider.ArgumentDescriptor descriptor = getDescriptor(arguments.isFirst, isFirst, arguments.type);

          assert !StringUtil.isEmptyOrSpaces(arguments.text);
          for (StringTokenizer st = new StringTokenizer(arguments.text, " \t\n\r,;"); st.hasMoreTokens(); ) {
            String name = st.nextToken();

            Object oldValue = res.put(name, descriptor);
            assert oldValue == null;
          }
        }
      }

      return res;
    }

    private static GroovyNamedArgumentProvider.ArgumentDescriptor getDescriptor(Boolean methodFirstFlag,
                                                                                Boolean attrFirstFlag,
                                                                                String type) {
      Boolean objShowFirst = attrFirstFlag;
      if (objShowFirst == null) {
        objShowFirst = methodFirstFlag;
      }

      boolean showFirst = objShowFirst != null && objShowFirst;

      if (StringUtil.isEmptyOrSpaces(type)) {
        return showFirst ? GroovyNamedArgumentProvider.TYPE_ANY : GroovyNamedArgumentProvider.TYPE_ANY_NOT_FIRST;
      }

      GroovyNamedArgumentProvider.ArgumentDescriptor descriptor = new GroovyNamedArgumentProvider.StringTypeCondition(type.trim());

      if (!showFirst) {
        descriptor.setShowFirst(false);
      }

      return descriptor;
    }
  }

  @Tag("attrs")
  public static class Arguments {
    @Attribute("type")
    public String type;

    @Attribute("showFirst")
    public Boolean isFirst;

    @Text
    public String text;
  }

  @Tag("attr")
  public static class Argument {
    @Attribute("type")
    public String type;

    @Attribute("showFirst")
    public Boolean isFirst;

    @Attribute("name")
    public String name;
  }
}
