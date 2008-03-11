package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DNamedElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DTypedElement;

/**
 * User: Dmitry.Krasilschikov
 * Date: 13.02.2008
 */

/*
 * Base class for Dynamic property and method
 */

public abstract class DItemElement implements DNamedElement, DTypedElement {
  public String myType = null;
  public String myName = null;

//  @NotNull
  public String myHightlightedText = null;

  public DItemElement(String name, String type) {
    myName = name;
    myType = type;
  }

  public String getHightlightedText() {
    return myHightlightedText;
  }

  public void setHightlightedText(String hightlightedText) {
    myHightlightedText = hightlightedText;
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DItemElement that = (DItemElement) o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;
    if (myType != null ? !myType.equals(that.myType) : that.myType != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myType != null ? myType.hashCode() : 0);
    result = 31 * result + (myName != null ? myName.hashCode() : 0);
    return result;
  }

  public String getType() {
    return myType;
  }

  public void setType(String type) {
    this.myType = type;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    this.myName = name;
  }
}