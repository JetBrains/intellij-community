package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiManager;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DItemElement;

import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
@State(
    name = "DynamicManagerImpl",
    storages = {
    @Storage(
        id = "myDir",
        file = "$WORKSPACE_FILE$"
    )}
)

public class DynamicManagerImpl extends DynamicManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManagerImpl");

  private final Project myProject;
  private List<DynamicChangeListener> myListeners = new ArrayList<DynamicChangeListener>();
  private DRootElement myRootElement = new DRootElement();

  public DynamicManagerImpl(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }


  public void initComponent() {
  }

  public void addProperty(DClassElement classElement, DPropertyElement propertyElement) {
    if (classElement == null) return;

    classElement.addProperty(propertyElement);
//    addToClassesMap(propertyElement, classElement);
  }

  public void addMethod(DClassElement classElement, DMethodElement methodElement) {
    if (classElement == null) return;

    classElement.addMethod(methodElement);
//    addToClassesMap(methodElement, classElement);
  }

//  private void addToClassesMap(DItemElement methodElement, DClassElement classElement) {
//    myItemsToClasses.put(methodElement, classElement);
//  }

  public void removeClassElement(String containingClassName) {

    final DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(containingClassName);

    fireChange();
  }

  public void removePropertyElement(DPropertyElement propertyElement) {
    final DClassElement classElement = getClassElementByItem(propertyElement);
    assert classElement != null;

    classElement.removeProperty(propertyElement.getName());
//    myItemsToClasses.remove(propertyElement);
    fireChange();
  }

  @NotNull
  public Collection<DPropertyElement> findDynamicPropertiesOfClass(String className) {
    final DClassElement classElement = findClassElement(getRootElement(), className);

    if (classElement != null) {
      return classElement.getProperties();
    }
    return new ArrayList<DPropertyElement>();
  }

  @NotNull
  public String[] getPropertiesNamesOfClass(String conatainingClassName) {
    final DClassElement classElement = findClassElement(getRootElement(), conatainingClassName);

    Set<String> result = new HashSet<String>();
    if (classElement != null) {
      for (DPropertyElement propertyElement : classElement.getProperties()) {
        result.add(propertyElement.getName());
      }
    }
    return result.toArray(new String[result.size()]);
  }

  @Nullable
  public String getPropertyType(String className, String propertyName) {
    final DPropertyElement dynamicProperty = findConcreteDynamicProperty(getRootElement(), className, propertyName);

    if (dynamicProperty == null) return null;
    return dynamicProperty.getType();
  }

  public void replaceDynamicMethod(DMethodElement oldMethod, DMethodElement newElement) {
//    final DClassElement className = oldMethod.getClassElement();
//    assert className.getName().equals(newElement.getClassElement().getName());

//    className.removeMethod(oldMethod);
  }

  @NotNull
  public Collection<DClassElement> getAllContainingClasses() {
    //TODO: use iterator
    final DRootElement root = getRootElement();

    return root.getContainingClasses();
  }

  public DRootElement getRootElement() {
    return myRootElement;
  }

  /*
  * Adds dynamicPropertyChange listener
  */
  public void addDynamicChangeListener(DynamicChangeListener listener) {
    myListeners.add(listener);
  }

  /*
  * Removes dynamicPropertyChange listener
  */
  public void removeDynamicChangeListener(DynamicChangeListener listener) {
    myListeners.remove(listener);
  }

  public void replaceDynamicProperty(DPropertyElement oldElement, DPropertyElement newElement) {
    removePropertyElement(oldElement);
  }

  /*
  * Changes dynamic property
  */

  public String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName) {
    final DClassElement classElement = findClassElement(getRootElement(), className);
    if (classElement == null) return null;

    final DPropertyElement oldPropertyElement = classElement.getPropertyByName(oldPropertyName);
    classElement.removeProperty(oldPropertyName);
    classElement.addProperty(new DPropertyElement(newPropertyName, oldPropertyElement.getType()));
    fireChange();

    return newPropertyName;
  }

  public String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType) {
    final DPropertyElement property = findConcreteDynamicProperty(className, propertyName);

    if (property == null) return null;

    property.setType(newPropertyType);
    return newPropertyType;
  }


  /*
  * Find dynamic property in class with name
  */

  @Nullable
  public DMethodElement findConcreteDynamicMethod(DRootElement rootElement, String conatainingClassName, String methodName, String[] parametersTypes) {
    DClassElement classElement = findClassElement(rootElement, conatainingClassName);
    if (classElement == null) return null;

    return classElement.getMethod(methodName, parametersTypes);
  }

   //  @Nullable

  public DMethodElement findConcreteDynamicMethod(String conatainingClassName, String name, String[] parameterTypes) {
    return findConcreteDynamicMethod(getRootElement(), conatainingClassName, name, parameterTypes);
  }

  @Nullable
  public String getMethodReturnType(String className, String methodName, String[] paramTypes) {
    final DMethodElement methodElement = findConcreteDynamicMethod(getRootElement(), className, methodName, paramTypes);

    if (methodElement == null) return null;

    return methodElement.getType();
  }

  public void removeMethodElement(DMethodElement methodElement) {
    final DClassElement classElement = getClassElementByItem(methodElement);
    assert classElement != null;

    classElement.removeMethod(methodElement);
//    myItemsToClasses.remove(methodElement);

    fireChange();
  }

  public void replaceDynamicMethodType(String className, String name, List<MyPair> myPairList, String oldType, String newType) {
    final DMethodElement method = findConcreteDynamicMethod(className, name, QuickfixUtil.getArgumentsTypes(myPairList));

    if (method == null) return;
    method.setType(newType);
  }

  @NotNull
  public DClassElement getOrCreateClassElement(Module module, String className, boolean binded) {
    DClassElement classElement = DynamicManager.getInstance(myProject).getRootElement().getClassElement(className);
    if (classElement == null) {
      return new DClassElement(module, className, binded);
    }

    return classElement;
  }

  @Nullable
  public DClassElement getClassElementByItem(DItemElement itemElement) {
    final Collection<DClassElement> classes = getAllContainingClasses();
    for (DClassElement aClass : classes) {
      if (aClass.containsElement(itemElement)) return aClass;
    }
    return null;
  }

  public String replaceClassName(String oldClassName, String newClassName) {
    final DRootElement rootElement = getRootElement();
    final DClassElement oldClassElement = findClassElement(rootElement, oldClassName);
    if (oldClassElement == null) return oldClassName;

    oldClassElement.setName(newClassName);

    fireChange();

    return newClassName;
  }

  public void fireChange() {
    for (DynamicChangeListener listener : myListeners) {
      listener.dynamicPropertyChange();
    }

    fireChangeCodeAnalyze();
    fireChangeToolWindow();
  }

  private void fireChangeToolWindow() {
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowWrapper.DYNAMIC_TOOLWINDOW_ID);
    window.getComponent().revalidate();
    window.getComponent().repaint();
  }

  private void fireChangeCodeAnalyze() {
    PsiManager.getInstance(myProject).dropResolveCaches();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Nullable
  public DPropertyElement findConcreteDynamicProperty(final String conatainingClassName, final String propertyName) {
    return findConcreteDynamicProperty(getRootElement(), conatainingClassName, propertyName);
  }

  @Nullable
  public DPropertyElement findConcreteDynamicProperty(DRootElement rootElement, final String conatainingClassName, final String propertyName) {
    final DClassElement classElement = rootElement.getClassElement(conatainingClassName);

    if (classElement == null) return null;

    return classElement.getPropertyByName(propertyName);
  }

  @Nullable
  private DClassElement findClassElement(DRootElement rootElement, final String conatainingClassName) {
    return rootElement.getClassElement(conatainingClassName);
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "DynamicManagerImpl";
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }

  /**
   * On exit
   */
  public Element getState() {
    return XmlSerializer.serialize(myRootElement);
  }

  /*
   * On loading
   */
  public void loadState(Element element) {
    myRootElement = XmlSerializer.deserialize(element, myRootElement.getClass());
  }
}