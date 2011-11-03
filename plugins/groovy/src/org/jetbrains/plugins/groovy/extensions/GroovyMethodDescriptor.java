package org.jetbrains.plugins.groovy.extensions;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.Nullable;

import java.util.*;

import static org.jetbrains.plugins.groovy.extensions.NamedArgumentDescriptor.*;

/**
 * @author Sergey Evdokimov
 */
public class GroovyMethodDescriptor {

  private static final String ATTR_NAMES_DELIMITER = " \t\n\r,;";

  @Attribute("name")
  public String methodName;

  @Attribute("checkParamsType")
  public Boolean checkParamsType;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Param[] params;

  @Tag("param")
  public static class Param {

    public static final Param[] EMPTY_ARRAY = new Param[0];

    @Attribute("type")
    public String type;
  }

  @Attribute("returnType")
  public String returnType;

  @Attribute("returnTypeCalculator")
  public String returnTypeCalculator;

  @Attribute("namedArgs")
  public String namedArgs;

  @Attribute("namedArgsProvider")
  public String namedArgsProvider;

  @Attribute("namedArgsShowFirst")
  public Boolean isNamedArgsShowFirst;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  //public Arguments[] arguments;
  public NamedArguments[] myArguments;

  @Tag("namedArguments")
  public static class NamedArguments {
    @Attribute("type")
    public String type;

    @Attribute("showFirst")
    public Boolean isFirst;

    @Attribute("names")
    public String names;
  }

  @Nullable
  public List<String> getParams() {
    if (params != null) {
      assert (checkParamsType == null || checkParamsType);
      
      String[] paramsTypeNames = new String[params.length];
      for (int i = 0; i < params.length; i++) {
        String typeName = params[i].type;
        assert StringUtil.isNotEmpty(typeName); 
        paramsTypeNames[i] = typeName;
      }
      
      return Arrays.asList(paramsTypeNames);
    }

    if (checkParamsType != null && checkParamsType) {
      return Collections.emptyList();
    }
    else {
      return null;
    }
  }

  @Nullable
  public Map<String, NamedArgumentDescriptor> getArgumentsMap() {
    if (myArguments == null && namedArgs == null) {
      assert isNamedArgsShowFirst == null;
      return null;
    }

    assert namedArgsProvider == null;

    Map<String, NamedArgumentDescriptor> res =
      new HashMap<String, NamedArgumentDescriptor>();

    if (myArguments != null) {
      for (NamedArguments arguments : myArguments) {
        NamedArgumentDescriptor descriptor = getDescriptor(isNamedArgsShowFirst, arguments.isFirst, arguments.type);

        assert !StringUtil.isEmptyOrSpaces(arguments.names);

        String names = arguments.names;

        for (StringTokenizer st = new StringTokenizer(names, ATTR_NAMES_DELIMITER); st.hasMoreTokens(); ) {
          String name = st.nextToken();

          Object oldValue = res.put(name, descriptor);
          assert oldValue == null;
        }
      }
    }

    if (!StringUtil.isEmptyOrSpaces(namedArgs)) {
      NamedArgumentDescriptor descriptor = getDescriptor(isNamedArgsShowFirst, null, null);

      for (StringTokenizer st = new StringTokenizer(namedArgs, ATTR_NAMES_DELIMITER); st.hasMoreTokens(); ) {
        String name = st.nextToken();

        Object oldValue = res.put(name, descriptor);
        assert oldValue == null : "Duplicated attribute name: " + name;
      }
    }

    return res;
  }

  private static NamedArgumentDescriptor getDescriptor(@Nullable Boolean methodFirstFlag,
                                                       @Nullable Boolean attrFirstFlag,
                                                       @Nullable String type) {
    Boolean objShowFirst = attrFirstFlag;
    if (objShowFirst == null) {
      objShowFirst = methodFirstFlag;
    }

    boolean showFirst = objShowFirst == null || objShowFirst;

    if (StringUtil.isEmptyOrSpaces(type)) {
      return showFirst ? SIMPLE_ON_TOP : SIMPLE_NORMAL;
    }

    NamedArgumentDescriptor descriptor = new NamedArgumentDescriptor.StringTypeCondition(type.trim());

    if (!showFirst) {
      descriptor.setPriority(Priority.NORMAL);
    }

    return descriptor;
  }
}
