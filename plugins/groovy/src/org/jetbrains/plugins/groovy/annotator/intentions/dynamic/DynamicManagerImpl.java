/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.*;
import com.intellij.psi.impl.PsiModificationTrackerImpl;
import com.intellij.ui.treeStructure.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.ui.DynamicElementSettings;

import javax.swing.tree.DefaultMutableTreeNode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
@State(name = "DynamicElementsStorage", storages = @Storage("dynamic.xml"))

public class DynamicManagerImpl extends DynamicManager {
  private final Project myProject;
  private DRootElement myRootElement = new DRootElement();

  public DynamicManagerImpl(final Project project) {
    myProject = project;
    StartupManager.getInstance(project).registerPostStartupActivity(() -> {
      if (!myRootElement.getContainingClasses().isEmpty()) {
        DynamicToolWindowWrapper.getInstance(project).getToolWindow(); //initialize myToolWindow
      }
    });
  }

  public Project getProject() {
    return myProject;
  }


  @Override
  public void initComponent() {
  }

  @Override
  public void addProperty(DynamicElementSettings settings) {
    assert settings != null;
    assert !settings.isMethod();

    final DPropertyElement propertyElement = (DPropertyElement) createDynamicElement(settings);
    final DClassElement classElement = getOrCreateClassElement(myProject, settings.getContainingClassName());

    ToolWindow window = DynamicToolWindowWrapper.getInstance(myProject).getToolWindow(); //important to fetch myToolWindow before adding
    classElement.addProperty(propertyElement);
    addItemInTree(classElement, propertyElement, window);
  }

  private void removeItemFromTree(DItemElement itemElement, DClassElement classElement) {
    DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(myProject);
    ListTreeTableModelOnColumns model = wrapper.getTreeTableModel();
    Object classNode = TreeUtil.findNodeWithObject(classElement, model, model.getRoot());
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) TreeUtil.findNodeWithObject(itemElement, model, classNode);
    if (node == null) return;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

    doRemove(wrapper, node, parent);
  }

  private void removeClassFromTree(DClassElement classElement) {
    DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(myProject);
    ListTreeTableModelOnColumns model = wrapper.getTreeTableModel();
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) TreeUtil.findNodeWithObject(classElement, model, model.getRoot());
    if (node == null) return;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

    doRemove(wrapper, node, parent);
  }

  private static void doRemove(DynamicToolWindowWrapper wrapper, DefaultMutableTreeNode node, DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode toSelect = (parent.getChildAfter(node) != null || parent.getChildCount() == 1 ?
        node.getNextNode() :
        node.getPreviousNode());


    wrapper.removeFromParent(parent, node);
    if (toSelect != null) {
      wrapper.setSelectedNode(toSelect);
    }
  }

  private void addItemInTree(final DClassElement classElement, final DItemElement itemElement, final ToolWindow window) {
    final ListTreeTableModelOnColumns myTreeTableModel = DynamicToolWindowWrapper.getInstance(myProject).getTreeTableModel();

    window.activate(() -> {
      final Object rootObject = myTreeTableModel.getRoot();
      if (!(rootObject instanceof DefaultMutableTreeNode)) return;
      final DefaultMutableTreeNode rootNode = (DefaultMutableTreeNode) rootObject;

      DefaultMutableTreeNode node = new DefaultMutableTreeNode(itemElement);
      if (rootNode.getChildCount() > 0) {
        for (DefaultMutableTreeNode classNode = (DefaultMutableTreeNode) rootNode.getFirstChild();
             classNode != null;
             classNode = (DefaultMutableTreeNode) rootNode.getChildAfter(classNode)) {

          final Object classRow = classNode.getUserObject();
          if (!(classRow instanceof DClassElement)) return;

          DClassElement otherClassName = (DClassElement) classRow;
          if (otherClassName.equals(classElement)) {
            int index = getIndexToInsert(classNode, itemElement);
            classNode.insert(node, index);
            myTreeTableModel.nodesWereInserted(classNode, new int[]{index});
            DynamicToolWindowWrapper.getInstance(myProject).setSelectedNode(node);
            return;
          }
        }
      }

      // if there is no such class in tree
      int index = getIndexToInsert(rootNode, classElement);
      DefaultMutableTreeNode classNode = new DefaultMutableTreeNode(classElement);
      rootNode.insert(classNode, index);
      myTreeTableModel.nodesWereInserted(rootNode, new int[]{index});

      classNode.add(node);
      myTreeTableModel.nodesWereInserted(classNode, new int[]{0});

      DynamicToolWindowWrapper.getInstance(myProject).setSelectedNode(node);
    }, true);
  }

  private static int getIndexToInsert(DefaultMutableTreeNode parent, DNamedElement namedElement) {
    if (parent.getChildCount() == 0) return 0;

    int res = 0;
    for (DefaultMutableTreeNode child = (DefaultMutableTreeNode) parent.getFirstChild();
         child != null;
         child = (DefaultMutableTreeNode) parent.getChildAfter(child)) {
      Object childObject = child.getUserObject();

      if (!(childObject instanceof DNamedElement)) return 0;

      String otherName = ((DNamedElement) childObject).getName();
      if (otherName.compareTo(namedElement.getName()) > 0) return res;
      res++;
    }
    return res;
  }

  @Override
  public void addMethod(DynamicElementSettings settings) {
    if (settings == null) return;
    assert settings.isMethod();

    final DMethodElement methodElement = (DMethodElement) createDynamicElement(settings);
    final DClassElement classElement = getOrCreateClassElement(myProject, settings.getContainingClassName());

    ToolWindow window = DynamicToolWindowWrapper.getInstance(myProject).getToolWindow(); //important to fetch myToolWindow before adding
    classElement.addMethod(methodElement);
    addItemInTree(classElement, methodElement, window);
  }

  @Override
  public void removeClassElement(DClassElement classElement) {

    final DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(classElement.getName());
    removeClassFromTree(classElement);
  }

  private void removePropertyElement(DPropertyElement propertyElement) {
    final DClassElement classElement = getClassElementByItem(propertyElement);
    assert classElement != null;

    classElement.removeProperty(propertyElement);
  }

  @Override
  @NotNull
  public Collection<DPropertyElement> findDynamicPropertiesOfClass(String className) {
    final DClassElement classElement = findClassElement(getRootElement(), className);

    if (classElement != null) {
      return classElement.getProperties();
    }
    return new ArrayList<>();
  }

  @Override
  @Nullable
  public String getPropertyType(String className, String propertyName) {
    final DPropertyElement dynamicProperty = findConcreteDynamicProperty(getRootElement(), className, propertyName);

    if (dynamicProperty == null) return null;
    return dynamicProperty.getType();
  }

  @Override
  @NotNull
  public Collection<DClassElement> getAllContainingClasses() {
    //TODO: use iterator
    final DRootElement root = getRootElement();

    return root.getContainingClasses();
  }

  @Override
  public DRootElement getRootElement() {
    return myRootElement;
  }

  @Override
  @Nullable
  public String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName) {
    final DClassElement classElement = findClassElement(getRootElement(), className);
    if (classElement == null) return null;

    final DPropertyElement oldPropertyElement = classElement.getPropertyByName(oldPropertyName);
    if (oldPropertyElement == null) return null;
    classElement.removeProperty(oldPropertyElement);
    classElement.addProperty(new DPropertyElement(oldPropertyElement.isStatic(), newPropertyName, oldPropertyElement.getType()));
    fireChange();
    DynamicToolWindowWrapper.getInstance(getProject()).rebuildTreePanel();


    return newPropertyName;
  }

  @Override
  @Nullable
  public String replaceDynamicPropertyType(String className, String propertyName, String oldPropertyType, String newPropertyType) {
    final DPropertyElement property = findConcreteDynamicProperty(className, propertyName);

    if (property == null) return null;

    property.setType(newPropertyType);
    fireChange();
    return newPropertyType;
  }

  /*
  * Find dynamic property in class with name
  */

  @Nullable
  private static DMethodElement findConcreteDynamicMethod(DRootElement rootElement,
                                                          String containingClassName,
                                                          String methodName,
                                                          String[] parametersTypes) {
    DClassElement classElement = findClassElement(rootElement, containingClassName);
    if (classElement == null) return null;

    return classElement.getMethod(methodName, parametersTypes);
  }

  //  @Nullable

  @Override
  public DMethodElement findConcreteDynamicMethod(String containingClassName, String name, String[] parameterTypes) {
    return findConcreteDynamicMethod(getRootElement(), containingClassName, name, parameterTypes);
  }

  private void removeMethodElement(DMethodElement methodElement) {
    final DClassElement classElement = getClassElementByItem(methodElement);
    assert classElement != null;

    classElement.removeMethod(methodElement);
  }

  @Override
  public void removeItemElement(DItemElement element) {
    DClassElement classElement = getClassElementByItem(element);
    if (classElement == null) return;
    
    if (element instanceof DPropertyElement) {
      removePropertyElement(((DPropertyElement) element));
    } else if (element instanceof DMethodElement) {
      removeMethodElement(((DMethodElement) element));
    }

    removeItemFromTree(element, classElement);
  }

  @Override
  public void replaceDynamicMethodType(String className, String name, List<ParamInfo> myPairList, String oldType, String newType) {
    final DMethodElement method = findConcreteDynamicMethod(className, name, QuickfixUtil.getArgumentsTypes(myPairList));

    if (method == null) return;
    method.setType(newType);
    fireChange();
  }

  @Override
  @NotNull
  public DClassElement getOrCreateClassElement(Project project, String className) {
    DClassElement classElement = DynamicManager.getInstance(myProject).getRootElement().getClassElement(className);
    if (classElement == null) {
      return new DClassElement(project, className);
    }

    return classElement;
  }

  @Override
  @Nullable
  public DClassElement getClassElementByItem(DItemElement itemElement) {
    final Collection<DClassElement> classes = getAllContainingClasses();
    for (DClassElement aClass : classes) {
      if (aClass.containsElement(itemElement)) return aClass;
    }
    return null;
  }

  @Override
  public void replaceDynamicMethodName(String className, String oldName, String newName, String[] types) {
    final DMethodElement oldMethodElement = findConcreteDynamicMethod(className, oldName, types);
    if (oldMethodElement != null) {
      oldMethodElement.setName(newName);
    }
    DynamicToolWindowWrapper.getInstance(getProject()).rebuildTreePanel();
    fireChange();
  }

  @Override
  public Iterable<PsiMethod> getMethods(final String classQname) {
    DClassElement classElement = getRootElement().getClassElement(classQname);
    if (classElement == null) return Collections.emptyList();
    return ContainerUtil.map(classElement.getMethods(), methodElement -> methodElement.getPsi(PsiManager.getInstance(myProject), classQname));
  }

  @Override
  public Iterable<PsiVariable> getProperties(final String classQname) {
    DClassElement classElement = getRootElement().getClassElement(classQname);
    if (classElement == null) return Collections.emptyList();
    return ContainerUtil.map(classElement.getProperties(), propertyElement -> propertyElement.getPsi(PsiManager.getInstance(myProject), classQname));
  }

  @Override
  public void replaceClassName(final DClassElement oldClassElement, String newClassName) {
    if (oldClassElement == null) return;

    final DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(oldClassElement.getName());

    oldClassElement.setName(newClassName);
    rootElement.mergeAddClass(oldClassElement);

    fireChange();
  }

  @Override
  public void fireChange() {
    fireChangeCodeAnalyze();
  }

  private void fireChangeCodeAnalyze() {
    final Editor textEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (textEditor == null) return;
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(textEditor.getDocument());
    if (file == null) return;

    ((PsiModificationTrackerImpl)PsiManager.getInstance(myProject).getModificationTracker()).incCounter();
    DaemonCodeAnalyzer.getInstance(myProject).restart();
  }

  @Override
  @Nullable
  public DPropertyElement findConcreteDynamicProperty(final String containingClassName, final String propertyName) {
    return findConcreteDynamicProperty(getRootElement(), containingClassName, propertyName);
  }

  @Nullable
  private static DPropertyElement findConcreteDynamicProperty(DRootElement rootElement, final String conatainingClassName, final String propertyName) {
    final DClassElement classElement = rootElement.getClassElement(conatainingClassName);

    if (classElement == null) return null;

    return classElement.getPropertyByName(propertyName);
  }

  @Nullable
  private static DClassElement findClassElement(DRootElement rootElement, final String conatainingClassName) {
    return rootElement.getClassElement(conatainingClassName);
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "DynamicManagerImpl";
  }

  @Override
  public void projectOpened() {
  }

  @Override
  public void projectClosed() {
  }

  /**
   * On exit
   */
  @Override
  public DRootElement getState() {
//    return XmlSerializer.serialize(myRootElement);
    return myRootElement;
  }

  /*
   * On loading
   */
  @Override
  public void loadState(DRootElement element) {
//    myRootElement = XmlSerializer.deserialize(element, myRootElement.getClass());
    myRootElement = element;
  }

  @Override
  public DItemElement createDynamicElement(DynamicElementSettings settings) {
    DItemElement itemElement;
    if (settings.isMethod()) {
      itemElement = new DMethodElement(settings.isStatic(), settings.getName(), settings.getType(), settings.getParams());
    } else {
      itemElement = new DPropertyElement(settings.isStatic(), settings.getName(), settings.getType());
    }
    return itemElement;
  }
}
