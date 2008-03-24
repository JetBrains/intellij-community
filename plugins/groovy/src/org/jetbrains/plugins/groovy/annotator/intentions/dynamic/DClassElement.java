package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;

import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 20.12.2007
 */
public class DClassElement implements DNamedElement {
  public String myName;
  public Map<String, DPropertyElement> myNamesToProperties = new HashMap<String, DPropertyElement>();
  public Set<DMethodElement> myMethods = new HashSet<DMethodElement>();
  private Project myProject;

  public DClassElement() {
  }

  public DClassElement(Project project, String name) {
    myProject = project;
    myName = name;

    DynamicManager.getInstance(myProject).getRootElement().mergeAddClass(this);
  }

  protected void addMethod(DMethodElement methodElement) {
    myMethods.add(methodElement);
  }

  protected void addMethods(Collection<DMethodElement> methods) {
    myMethods.addAll(methods);
  }

  protected void addProperty(DPropertyElement propertyElement) {
    myNamesToProperties.put(propertyElement.getName(), propertyElement);
  }

  protected void addProperties(Collection<DPropertyElement> properties) {
    for (DPropertyElement property : properties) {
      myNamesToProperties.put(property.getName(), property);
    }
  }

  public DPropertyElement getPropertyByName(String propertyName) {
    return myNamesToProperties.get(propertyName);
  }

  public Collection<DPropertyElement> getProperties() {
    return myNamesToProperties.values();
  }

  public Set<DMethodElement> getMethods() {
    return myMethods;
  }

  public String getName() {
    return myName;
  }

  public void setName(String name) {
    myName = name;
  }

  protected DPropertyElement removeProperty(String name) {
    return myNamesToProperties.remove(name);
  }

  protected boolean removeMethod(DMethodElement methodElement) {
    return myMethods.remove(methodElement);
  }

  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    DClassElement that = (DClassElement) o;

    if (myName != null ? !myName.equals(that.myName) : that.myName != null) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = (myName != null ? myName.hashCode() : 0);
    result = 31 * result + (myNamesToProperties != null ? myNamesToProperties.hashCode() : 0);
    result = 31 * result + (myMethods != null ? myMethods.hashCode() : 0);
    return result;
  }

  @Nullable
  public DMethodElement getMethod(String methodName, String[] parametersTypes) {
    for (DMethodElement method : myMethods) {
      final List<MyPair> myPairList = method.getPairs();
      if (method.getName().equals(methodName)
          && Arrays.equals(QuickfixUtil.getArgumentsTypes(myPairList), parametersTypes)) return method;
    }
    return null;
  }

  public DClassElement bindToTree() {
    return DynamicManager.getInstance(myProject).getRootElement().mergeAddClass(this);
  }

  public boolean containsElement(DItemElement itemElement){
    return myNamesToProperties.keySet().contains(itemElement.getName()) ||
        (itemElement instanceof DMethodElement && myMethods.contains(itemElement));
  }
}
