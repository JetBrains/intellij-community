package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.*;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.tree.TreeUtil;
import com.intellij.util.ui.treetable.ListTreeTableModelOnColumns;
import com.intellij.util.xmlb.XmlSerializer;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.*;
import org.jetbrains.plugins.groovy.lang.psi.impl.GroovyPsiManager;

import javax.swing.tree.DefaultMutableTreeNode;
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
    addItemInTree(classElement, propertyElement);
  }

  private void removeItemFromTree(DItemElement itemElement, DClassElement classElement) {
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowWrapper.DYNAMIC_TOOLWINDOW_ID);
    DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(myProject);
    ListTreeTableModelOnColumns model = wrapper.getTreeTableModel(window);
    Object classNode = TreeUtil.findNodeWithObject(classElement, model, model.getRoot());
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) TreeUtil.findNodeWithObject(itemElement, model, classNode);
    if (node == null) return;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

    doRemove(wrapper, node, parent);
  }

  private void removeClassFromTree(DClassElement classElement) {
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowWrapper.DYNAMIC_TOOLWINDOW_ID);
    DynamicToolWindowWrapper wrapper = DynamicToolWindowWrapper.getInstance(myProject);
    ListTreeTableModelOnColumns model = wrapper.getTreeTableModel(window);
    final DefaultMutableTreeNode node = (DefaultMutableTreeNode) TreeUtil.findNodeWithObject(classElement, model, model.getRoot());
    if (node == null) return;
    DefaultMutableTreeNode parent = (DefaultMutableTreeNode) node.getParent();

    doRemove(wrapper, node, parent);
  }

  private void doRemove(DynamicToolWindowWrapper wrapper, DefaultMutableTreeNode node, DefaultMutableTreeNode parent) {
    DefaultMutableTreeNode toSelect = (parent.getChildAfter(node) != null || parent.getChildCount() == 1 ?
        node.getNextNode() :
        node.getPreviousNode());


    wrapper.removeFromParent(parent, node);
    if (toSelect != null) {
      wrapper.setSelectedNode(toSelect);
    }
  }

  private void addItemInTree(final DClassElement classElement, final DItemElement itemElement) {
    final ToolWindow window = ToolWindowManager.getInstance(myProject).getToolWindow(DynamicToolWindowWrapper.DYNAMIC_TOOLWINDOW_ID);
    final ListTreeTableModelOnColumns myTreeTableModel = DynamicToolWindowWrapper.getInstance(myProject).getTreeTableModel(window);

    window.activate(new Runnable() {
      public void run() {
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
      }
    }, true);
  }

  private int getIndexToInsert(DefaultMutableTreeNode parent, DNamedElement namedElement) {
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

  public void addMethod(DClassElement classElement, DMethodElement methodElement) {
    if (classElement == null) return;

    classElement.addMethod(methodElement);
    addItemInTree(classElement, methodElement);
  }

  public void removeClassElement(DClassElement classElement) {

    final DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(classElement.getName());
    removeClassFromTree(classElement);
  }

  private void removePropertyElement(DPropertyElement propertyElement) {
    final DClassElement classElement = getClassElementByItem(propertyElement);
    assert classElement != null;

    classElement.removeProperty(propertyElement.getName());
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

  /*
  * Changes dynamic property
  */

  public String replaceDynamicPropertyName(String className, String oldPropertyName, String newPropertyName) {
    final DClassElement classElement = findClassElement(getRootElement(), className);
    if (classElement == null) return null;

    final DPropertyElement oldPropertyElement = classElement.getPropertyByName(oldPropertyName);
    if (oldPropertyElement == null) return null;
    classElement.removeProperty(oldPropertyName);
    classElement.addProperty(new DPropertyElement(newPropertyName, oldPropertyElement.getType()));
    fireChange();

    return newPropertyName;
  }

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
  public DMethodElement findConcreteDynamicMethod(DRootElement rootElement, String conatainingClassName, String methodName, String[] parametersTypes) {
    DClassElement classElement = findClassElement(rootElement, conatainingClassName);
    if (classElement == null) return null;

    return classElement.getMethod(methodName, parametersTypes);
  }

  //  @Nullable

  public DMethodElement findConcreteDynamicMethod(String conatainingClassName, String name, String[] parameterTypes) {
    return findConcreteDynamicMethod(getRootElement(), conatainingClassName, name, parameterTypes);
  }

  private void removeMethodElement(DMethodElement methodElement) {
    final DClassElement classElement = getClassElementByItem(methodElement);
    assert classElement != null;

    classElement.removeMethod(methodElement);
  }

  public void removeItemElement(DItemElement element) {
    DClassElement classElement = getClassElementByItem(element);
    if (element instanceof DPropertyElement) {
      removePropertyElement(((DPropertyElement) element));
    } else if (element instanceof DMethodElement) {
      removeMethodElement(((DMethodElement) element));
    }

    removeItemFromTree(element, classElement);
  }

  public void replaceDynamicMethodType(String className, String name, List<MyPair> myPairList, String oldType, String newType) {
    final DMethodElement method = findConcreteDynamicMethod(className, name, QuickfixUtil.getArgumentsTypes(myPairList));

    if (method == null) return;
    method.setType(newType);
    fireChange();
  }

  @NotNull
  public DClassElement getOrCreateClassElement(Project project, String className) {
    DClassElement classElement = DynamicManager.getInstance(myProject).getRootElement().getClassElement(className);
    if (classElement == null) {
      return new DClassElement(project, className);
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

  public void replaceDynamicMethodName(String className, String oldName, String newName, String[] types) {
    final DMethodElement oldMethodElement = findConcreteDynamicMethod(className, oldName, types);
    if (oldMethodElement != null) {
      oldMethodElement.setName(newName);
    }
  }

  public Iterable<PsiMethod> getMethods(final String classQname) {
    DClassElement classElement = getRootElement().getClassElement(classQname);
    if (classElement == null) return Collections.emptyList();
    return ContainerUtil.map(classElement.getMethods(), new Function<DMethodElement, PsiMethod>() {
      public PsiMethod fun(DMethodElement methodElement) {
        return methodElement.getPsi(PsiManager.getInstance(myProject), classQname);
      }
    });
  }

  public Iterable<PsiVariable> getProperties(final String classQname) {
    DClassElement classElement = getRootElement().getClassElement(classQname);
    if (classElement == null) return Collections.emptyList();
    return ContainerUtil.map(classElement.getProperties(), new Function<DPropertyElement, PsiVariable>() {
      public PsiVariable fun(DPropertyElement propertyElement) {
        return propertyElement.getPsi(PsiManager.getInstance(myProject), classQname);
      }
    });
  }

  public void replaceClassName(final DClassElement oldClassElement, String newClassName) {
    if (oldClassElement == null) return;

    final DRootElement rootElement = getRootElement();
    rootElement.removeClassElement(oldClassElement.getName());

    oldClassElement.setName(newClassName);
    rootElement.mergeAddClass(oldClassElement);

    fireChange();
  }

  public void fireChange() {
    for (DynamicChangeListener listener : myListeners) {
      listener.dynamicPropertyChange();
    }

    fireChangeCodeAnalyze();
  }

  private void fireChangeCodeAnalyze() {
    final Editor textEditor = FileEditorManager.getInstance(myProject).getSelectedTextEditor();
    if (textEditor == null) return;
    final PsiFile file = PsiDocumentManager.getInstance(myProject).getPsiFile(textEditor.getDocument());
    if (file == null) return;

    GroovyPsiManager.getInstance(myProject).dropTypesCache();
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