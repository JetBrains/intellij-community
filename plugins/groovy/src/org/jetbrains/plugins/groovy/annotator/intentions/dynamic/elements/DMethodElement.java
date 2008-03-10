package org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements;

import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.MyPair;

import java.util.ArrayList;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 12.02.2008
 */
public class DMethodElement extends DItemElement implements Comparable{
  public List<MyPair> myPairs = new ArrayList<MyPair>();

  public DMethodElement() {
    super(null, null);
  }

  public DMethodElement(String name, String returnType, List<MyPair> pairs) {
    super(name, returnType);

    myPairs = pairs;
  }

  public List<MyPair> getPairs() {
    return myPairs;
  }

  public int compareTo(Object o) {
    if (!(o instanceof DMethodElement)) return 0;
    final DMethodElement otherMethod = (DMethodElement) o;

    return getName().compareTo(otherMethod.getName()) + getType().compareTo(otherMethod.getType());
  }
}