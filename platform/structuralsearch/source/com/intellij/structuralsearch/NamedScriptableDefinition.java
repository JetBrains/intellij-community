package com.intellij.structuralsearch;

import com.intellij.openapi.util.JDOMExternalizable;
import org.jdom.Attribute;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

/**
 * @author Maxim.Mossienko
 * Date: 11.06.2009
 * Time: 12:55:39
 */
public class NamedScriptableDefinition implements JDOMExternalizable, Cloneable {
  @NonNls private static final String NAME = "name";
  @NonNls private static final String SCRIPT = "script";
  private String name;
  private String scriptCodeConstraint = "";

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getScriptCodeConstraint() {
    return scriptCodeConstraint;
  }

  public void setScriptCodeConstraint(String scriptCodeConstraint) {
    if ("\"\"".equals(scriptCodeConstraint)) {
      this.scriptCodeConstraint = "";
    }
    else {
      this.scriptCodeConstraint = scriptCodeConstraint;
    }
  }

  public Object clone() {
    try {
      return super.clone();
    } catch(CloneNotSupportedException ex) {
      return null;
    }
  }

  public void readExternal(Element element) {
    Attribute attribute = element.getAttribute(NAME);
    if (attribute != null) {
      name = attribute.getValue();
    }

    String s = element.getAttributeValue(SCRIPT);
    if (s != null) {
      scriptCodeConstraint = s;
    }
  }

  public void writeExternal(Element element) {
    element.setAttribute(NAME,name);
    if (scriptCodeConstraint.length() > 0) element.setAttribute(SCRIPT,scriptCodeConstraint);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof NamedScriptableDefinition)) return false;

    NamedScriptableDefinition that = (NamedScriptableDefinition)o;

    if (name != null ? !name.equals(that.name) : that.name != null) return false;
    if (scriptCodeConstraint != null ? !scriptCodeConstraint.equals(that.scriptCodeConstraint) : that.scriptCodeConstraint != null) {
      return false;
    }

    return true;
  }

  @Override
  public int hashCode() {
    int result = name != null ? name.hashCode() : 0;
    result = 31 * result + (scriptCodeConstraint != null ? scriptCodeConstraint.hashCode() : 0);
    return result;
  }
}
