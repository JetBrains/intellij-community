package org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.path;

import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.util.xmlb.annotations.AbstractCollection;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;

/**
 * @author Sergey Evdokimov
 */
public class GrMethodReturnTypeDescriptor {

  public static final ExtensionPointName<GrMethodReturnTypeDescriptor> EP_NAME =
    new ExtensionPointName<GrMethodReturnTypeDescriptor>("org.intellij.groovy.methodDescriptor");

  @Attribute("class")
  public String className;

  @Attribute("method")
  public String methodName;

  @Attribute("returnType")
  public String returnType;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public AnyParams[] anyParams;

  @Property(surroundWithTag = false)
  @AbstractCollection(surroundWithTag = false)
  public Param[] params;

  @Tag("param")
  public static class Param {

    public static final Param[] EMPTY_ARRAY = new Param[0];

    @Attribute("type")
    public String type;
  }

  @Tag("anyParams")
  public static class AnyParams {

  }
}
