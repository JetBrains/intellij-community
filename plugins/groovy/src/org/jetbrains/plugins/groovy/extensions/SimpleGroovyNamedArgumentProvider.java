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

  private static final String ATTR_NAMES_DELIMITER = " \t\n\r,;";

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

    @Attribute("attrNames")
    public String attrNames;

    @Attribute("showFirst")
    public Boolean isFirst;

    @Property(surroundWithTag = false)
    @AbstractCollection(surroundWithTag = false)
    //public Arguments[] arguments;
    public Arguments[] myArguments;

    public Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> getArgumentsMap() {
      Map<String, GroovyNamedArgumentProvider.ArgumentDescriptor> res =
        new HashMap<String, GroovyNamedArgumentProvider.ArgumentDescriptor>();

      if (myArguments != null) {
        for (Arguments arguments : myArguments) {
          GroovyNamedArgumentProvider.ArgumentDescriptor descriptor = getDescriptor(isFirst, arguments.isFirst, arguments.type);

          assert StringUtil.isEmptyOrSpaces(arguments.names) != StringUtil.isEmptyOrSpaces(arguments.text);

          String names = arguments.names;
          if (StringUtil.isEmptyOrSpaces(names)) {
            names = arguments.text;
          }

          for (StringTokenizer st = new StringTokenizer(names, ATTR_NAMES_DELIMITER); st.hasMoreTokens(); ) {
            String name = st.nextToken();

            Object oldValue = res.put(name, descriptor);
            assert oldValue == null;
          }
        }
      }

      if (!StringUtil.isEmptyOrSpaces(attrNames)) {
        GroovyNamedArgumentProvider.ArgumentDescriptor descriptor = getDescriptor(isFirst, null, null);

        for (StringTokenizer st = new StringTokenizer(attrNames, ATTR_NAMES_DELIMITER); st.hasMoreTokens(); ) {
          String name = st.nextToken();

          Object oldValue = res.put(name, descriptor);
          assert oldValue == null : "Duplicated attribute name: " + name;
        }
      }

      return res;
    }

    private static GroovyNamedArgumentProvider.ArgumentDescriptor getDescriptor(@Nullable Boolean methodFirstFlag,
                                                                                @Nullable Boolean attrFirstFlag,
                                                                                @Nullable String type) {
      Boolean objShowFirst = attrFirstFlag;
      if (objShowFirst == null) {
        objShowFirst = methodFirstFlag;
      }

      boolean showFirst = objShowFirst == null || objShowFirst;

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

    @Attribute("names")
    public String names;

    @Text
    public String text;
  }
}
