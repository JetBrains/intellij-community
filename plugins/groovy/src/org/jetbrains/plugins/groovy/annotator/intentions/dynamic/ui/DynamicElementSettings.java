package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;

import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 28.04.2008
 */
public class DynamicElementSettings {
  private String myName;
  private String myContainingClassName;
  private String myType;
  private boolean isMethod;
  private List<MyPair> myPairs;
  private boolean isStatic;

  public void setContainingClassName(String newName) {
    myContainingClassName = newName;
  }

  public String getContainingClassName() {
    return myContainingClassName;
  }

  public String getType() {
    return myType;
  }

  public void setType(String type) {
    this.myType = type;
  }

  public boolean isMethod() {
    return isMethod;
  }

  public void setMethod(boolean method) {
    isMethod = method;
  }

  public List<MyPair> getPairs() {
    return myPairs;
  }

  public void setPairs(List<MyPair> pairs) {
    myPairs = pairs;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  public boolean isStatic() {
      return isStatic;
  }

  public void setStatic(boolean aStatic) {
    isStatic = aStatic;
  }
}
