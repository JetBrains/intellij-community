package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer;
import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.openapi.wm.ToolWindowManager;
import com.intellij.psi.PsiManager;
import com.intellij.psi.PsiSubstitutor;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypeParameter;
import com.intellij.psi.util.MethodSignature;
import com.intellij.psi.util.MethodSignatureUtil;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.JDOMException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.QuickfixUtil;
import static org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DElement.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DMethodElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.elements.DPropertyElement;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualMethod;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.virtual.DynamicVirtualProperty;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
public class DynamicManagerImpl extends DynamicManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicManagerImpl");

  private final Project myProject;
  private Map<Module, File> myPathsToXmls;
  private List<DynamicChangeListener> myListeners = new ArrayList<DynamicChangeListener>();

  public DynamicManagerImpl(Project project) {
    myProject = project;
  }

  public Project getProject() {
    return myProject;
  }

  public void initComponent() {
    myPathsToXmls = initDynamicProperties();

    StartupManager startupManager = StartupManager.getInstance(getProject());
    ((StartupManagerEx) startupManager).registerPreStartupActivity(new Runnable() {
      public void run() {
        myPathsToXmls = initDynamicProperties();
      }
    });
  }

  private Map<Module, File> initDynamicProperties() {
    ModuleManager manager = ModuleManager.getInstance(getProject());

    Map<Module, File> xmls = new HashMap<Module, File>();
    for (Module module : manager.getModules()) {
      xmls.put(module, initializeDynamicProperties(module));
    }

    return xmls;
  }

  private File initializeDynamicProperties(Module module) {
    final String moduleDynPath = getOrCreateProjectDynamicDir() + File.separatorChar +
        DYNAMIC_PROPERTIES_MODULE + "_" + module.getName() + "_" +
        module.getModuleFilePath().hashCode() + ".xml";

    return new File(getOrCreateFile(moduleDynPath));
  }

  @NotNull
  private String getOrCreateProjectDynamicDir() {
    final String url = getProject().getPresentableUrl();
    final String projectDynPath = getOrCreateDynamicDirectory() + File.separatorChar +
        DYNAMIC_PROPERTIES_PROJECT + "_" + getProject().getName() + "_" +
        (url != null ? url : getProject().getName()).hashCode();

    return getOrCreateDir(projectDynPath);
  }

  private String getOrCreateDynamicDirectory() {
    final String dynDirPath = PathManager.getSystemPath() + File.separatorChar + DYNAMIC_PROPERTIES_DIR;
    return getOrCreateDir(dynDirPath);
  }

  private String getOrCreateFile(String dynDirPath) {
    try {
      final File dynDir = new File(dynDirPath);
      if (!dynDir.exists()) {
        dynDir.createNewFile();
      }

      return dynDir.getCanonicalPath();
    } catch (IOException e) {
      System.out.println("Error while save file" + dynDirPath);
      return PathManager.getSystemPath();
    }
  }

  private String getOrCreateDir(String dynDirPath) {
    try {
      final File dynDir = new File(dynDirPath);
      if (!dynDir.exists()) {
        dynDir.mkdir();
      }

      return dynDir.getCanonicalPath();
    } catch (IOException e) {
      System.out.println("Error while save file" + dynDirPath);
      return PathManager.getSystemPath();
    }
  }

  @Nullable
  public DynamicVirtualElement addDynamicElement(final DynamicVirtualElement virtualElement) {
    final String moduleName = virtualElement.getModuleName();
    Element rootElement = getRootElement(moduleName);

    final String containingClassName = virtualElement.getContainingClassName();
    final String elementName = virtualElement.getName();

    final boolean isProperty = isVirtualProperty(virtualElement);
    final boolean isMethod = isVirtualMethod(virtualElement);

    assert !(isMethod && isProperty);

    Element element = null;
    if (isProperty) {
      element = findConcreteDynamicProperty(rootElement, containingClassName, elementName);
    } else if (isMethod) {
      final List<MyPair<String, PsiType>> list = ((DynamicVirtualMethod) virtualElement).getArguments();
      final PsiType[] psiTypes = QuickfixUtil.getArgumentsTypes(list);
      element = findConcreteDynamicMethod(rootElement, containingClassName, elementName, psiTypes);
    }

    final Element classElement;
    if (element == null) {
      classElement = findDynamicClassElement(rootElement, virtualElement.getContainingClassName());

      if (classElement == null) {
        final DContainingClassElement containingClassElement = new DContainingClassElement(virtualElement.getContainingClassName());

        addDynamicElementToClass(virtualElement, containingClassElement);
        rootElement.addContent(containingClassElement);
      } else {
        addDynamicElementToClass(virtualElement, classElement);
      }
    }

    writeXMLTree(moduleName, rootElement);

    fireChange();

    return virtualElement;
  }

  private void addDynamicElementToClass(DynamicVirtualElement virtualElement, Element classElement) {
    if (virtualElement instanceof DynamicVirtualMethod) {
      classElement.addContent(new DMethodElement(((DynamicVirtualMethod) virtualElement), true));
    } else if (virtualElement instanceof DynamicVirtualProperty) {
      classElement.addContent(new DPropertyElement(((DynamicVirtualProperty) virtualElement)));
    }
  }

  private boolean isVirtualProperty(DynamicVirtualElement virtualElement) {
    return virtualElement instanceof DynamicVirtualProperty;
  }

  private boolean isVirtualMethod(DynamicVirtualElement virtualElement) {
    return virtualElement instanceof DynamicVirtualMethod;
  }

  private void writeXMLTree(String moduleName, Element rootElement) {
    FileWriter writer = null;
    final File filePath = myPathsToXmls.get(ModuleManager.getInstance(getProject()).findModuleByName(moduleName));
    try {
      writer = new FileWriter(filePath);
      JDOMUtil.writeElement(rootElement, writer, "\n");
    } catch (IOException e) {
      LOG.error("File " + filePath + " cannot be written.");
    } finally {
      try {
        if (writer != null) {
          writer.flush();
          writer.close();
        }
      } catch (IOException e) {
        LOG.error("FileWriter for file " + filePath + " cannot be close.");
      }
    }
  }

  private Document loadModuleDynXML(String moduleName) {
    Document document;
    try {
      final Module module = ModuleManager.getInstance(getProject()).findModuleByName(moduleName);

      document = module == null ? new Document() : JDOMUtil.loadDocument(myPathsToXmls.get(module));
    } catch (JDOMException e) {
      document = new Document();
    } catch (IOException e) {
      document = new Document();
    }

    if (!document.hasRootElement()) {
      document.setRootElement(new DRootElement());
    }
    return document;
  }

  @Nullable
  public DynamicVirtualElement removeDynamicElement(DynamicVirtualElement virtualElement) {
    final Document document = loadModuleDynXML(virtualElement.getModuleName());
    final String containingClassName = virtualElement.getContainingClassName();
    final String propertyName = virtualElement.getName();
    final String moduleName = virtualElement.getModuleName();

    final Element rootElement = document.getRootElement();
    final Element foundDynamicProperty = findConcreteDynamicProperty(rootElement, containingClassName, propertyName);
    if (foundDynamicProperty == null) return null;

    foundDynamicProperty.getParent().removeContent(foundDynamicProperty);

    writeXMLTree(moduleName, rootElement);

    fireChange();
    return virtualElement;
  }

  public void removeDynamicPropertiesOfClass(String moduleName, String containingClassName) {
    final Document document = loadModuleDynXML(moduleName);

    final Element rootElement = document.getRootElement();
    final Element classElement = findDynamicClassElement(rootElement, containingClassName);
    classElement.getParent().removeContent();

    fireChange();
  }

//  @Nullable
//  public Element findConcreteDynamicProperty(GrReferenceExpression referenceExpression, final String moduleName, final String containingClassName, final String propertyName) {
//    final PsiClassType type = referenceExpression.getManager().getElementFactory().createTypeByFQClassName(containingClassName, referenceExpression.getResolveScope());
//
//    final PsiClass psiClass = type.resolve();
//    if (psiClass == null) return null;
//
//    final Set<PsiClass> classes = new HashSet<PsiClass>();
//    classes.addAll(Arrays.asList(psiClass.getSupers()));
//
//    Element result = findConcreteDynamicProperty(getRootElement(moduleName), psiClass.getQualifiedName(), propertyName);
//    if (result != null) return result;
//
//    for (PsiClass aClass : classes) {
//      result = findConcreteDynamicProperty(getRootElement(moduleName), aClass.getQualifiedName(), propertyName);
//
//      if (result != null) return result;
//    }
//    return null;
//  }

//  @Nullable
//  public Element findConcreteDynamicElementWithSupers(String moduleName, final String conatainingClassName, final String propertyName) {
//
//    return findConcreteDynamicProperty(getRootElement(moduleName), conatainingClassName, propertyName);
//  }

//  @Nullable
//  protected String getTypeOfDynamicProperty(String moduleName, String containingClassName, String propertyName) {
//    if (containingClassName == null) return null;
//    final Element dynamicProperty = findConcreteDynamicProperty(moduleName, containingClassName, propertyName);
//    if (dynamicProperty == null) return null;
//
//    final List types = dynamicProperty.getContent(DynamicFiltersFactory.createPropertyTypeTagFilter());
//    if (types == null || (types.size() != 1)) return null;
//
//    final Object type = types.get(0);
//    if (!(type instanceof Element)) return null;
//
//    return ((Element) type).getText();
//  }

  @NotNull
  public String[] findDynamicPropertiesOfClass(String moduleName, String className) {
    Document document = loadModuleDynXML(moduleName);

    final Element containingClassElement = findDynamicClassElement(document.getRootElement(), className);
    if (containingClassElement == null) return new String[0];

    final List propertiesTagsOfClass = containingClassElement.getContent(DynamicFiltersFactory.createPropertyTagFilter());

    List<String> result = new ArrayList<String>();
    for (Object o : propertiesTagsOfClass) {
      result.add(((Element) o).getAttributeValue(NAME_ATTRIBUTE));
    }

    return result.toArray(new String[result.size()]);
  }

  @Nullable
  public String getPropertyType(String moduleName, String className, String propertyName) {
    final Element dynamicProperty = findConcreteDynamicProperty(getRootElement(moduleName), className, propertyName);

    if (dynamicProperty == null) return null;
    return dynamicProperty.getAttributeValue(TYPE_ATTRIBUTE);
  }

  @NotNull
  public DynamicVirtualProperty[] getAllDynamicProperties(String moduleName) {
    Set<DynamicVirtualProperty> result = new HashSet<DynamicVirtualProperty>();
    Document document = loadModuleDynXML(moduleName);

    final List definedContainingClasses = document.getRootElement().getContent(DynamicFiltersFactory.createContainingClassTagFilter());

    for (Object definedContainingClass : definedContainingClasses) {
      final Element containingClass = (Element) definedContainingClass;

      final String qualifiedName = containingClass.getAttributeValue(CONTAINIG_CLASS_TYPE_ATTRIBUTE);
      final List propertyTags = containingClass.getContent(DynamicFiltersFactory.createPropertyTagFilter());

      for (Object propertyTag : propertyTags) {
        final String propertyName = ((Element) propertyTag).getAttributeValue(NAME_ATTRIBUTE);
        final String propertyType = getPropertyType(moduleName, qualifiedName, propertyName);
        result.add(new DynamicVirtualProperty(propertyName, qualifiedName, moduleName, propertyType));
      }
    }

    return result.toArray(DynamicVirtualProperty.EMPTY_ARRAY);
  }

  @NotNull
  public Set<String> getAllContainingClasses(String moduleName) {
    final Element root = getRootElement(moduleName);
    Set<String> result = new HashSet<String>();

    final List definedContainingClasses = root.getContent(DynamicFiltersFactory.createContainingClassTagFilter());
    for (Object definedContainingClass : definedContainingClasses) {
      if (!(definedContainingClass instanceof Element)) continue;

      result.add(((Element) definedContainingClass).getAttributeValue(CONTAINIG_CLASS_TYPE_ATTRIBUTE));
    }

    return result;
  }

  public Element getRootElement(String moduleName) {
    return loadModuleDynXML(moduleName).getRootElement();
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

  public DynamicVirtualProperty replaceDynamicProperty(DynamicVirtualProperty oldProperty, DynamicVirtualProperty newProperty) {
    if (!oldProperty.getModuleName().equals(newProperty.getModuleName())
        || !oldProperty.getContainingClassName().equals(oldProperty.getContainingClassName()))
      return null;

    replaceDynamicProperty(oldProperty.getModuleName(),
        oldProperty.getContainingClassName(),
        oldProperty.getName(),
        newProperty.getName());
    return newProperty;
  }

  /*
  * Changes dynamic property
  */
  public String replaceDynamicProperty(String moduleName, String className, String oldPropertyName, String newPropertyName) {
    final Element rootElement = getRootElement(moduleName);
    final Element oldDynamicProperty = findConcreteDynamicProperty(rootElement, className, oldPropertyName);

    if (oldDynamicProperty == null) return oldPropertyName;

    oldDynamicProperty.setAttribute(NAME_ATTRIBUTE, newPropertyName);

    writeXMLTree(moduleName, rootElement);
    fireChange();

    return newPropertyName;
  }

  /*
  * Find dynamic property in class with name
  */
  @Nullable
  public Element findConcreteDynamicMethod(Element rootElement, String conatainingClassName, String methodName, PsiType[] parametersTypes) {
    Element definedClass = findDynamicClassElement(rootElement, conatainingClassName);
    if (definedClass == null) return null;

    final List methodsresult = definedClass.getContent(DynamicFiltersFactory.createConcreteMethodWithParametersFilter(methodName, parametersTypes));
    if (methodsresult == null || methodsresult.toArray().length != 1) return null;

    return ((Element) methodsresult.get(0));
  }

  /*
  * Find dynamic property in class with name
  */
  @Nullable
  public Element[] findConcreteDynamicMethodsWithName(String moduleName, String conatainingClassName, String name) {
    final Element rootElement = getRootElement(moduleName);
    Element definedClass = findDynamicClassElement(rootElement, conatainingClassName);
    if (definedClass == null) return null;

    final List xmlMethods = definedClass.getContent(DynamicFiltersFactory.createConcreteMethodNameFilter(name));

    if (!(xmlMethods.toArray() instanceof Element[])) return null;
    return ((Element[]) xmlMethods.toArray());
  }

  //  @Nullable
  public Element findConcreteDynamicMethod(String moduleName, String conatainingClassName, String name, PsiType[] parameterTypes) {
    return findConcreteDynamicMethod(getRootElement(moduleName), conatainingClassName, name, parameterTypes);
  }

  @NotNull
  public Set<MethodSignature> findMethodsSignaturesOfClass(String moduleName, String className) {
    final Element rootElement = getRootElement(moduleName);
    Set<MethodSignature> methodSignatures = new HashSet<MethodSignature>();

    final Element containingClassElement = findDynamicClassElement(rootElement, className);
    if (containingClassElement == null) return methodSignatures;

    final List methods = containingClassElement.getContent(DynamicFiltersFactory.createMethodTagFilter());

//    Set<String> result = new HashSet<String>();
    for (Object o : methods) {
      final Element method = (Element) o;
      final String methodName = method.getAttributeValue(NAME_ATTRIBUTE);

      final List parameters = method.getContent(DynamicFiltersFactory.createParameterTagFilter());

      List<PsiType> types = new ArrayList<PsiType>();
      for (Object parameterElement : parameters) {
        final Element parameter = (Element) parameterElement;

        final String name = parameter.getAttributeValue(NAME_ATTRIBUTE);
        final String type = parameter.getAttributeValue(TYPE_ATTRIBUTE);

        PsiType psiType = PsiManager.getInstance(myProject).getElementFactory().createTypeByFQClassName(type, myProject.getAllScope());
        types.add(psiType);
      }

      final MethodSignature signature = MethodSignatureUtil.createMethodSignature(methodName, types.toArray(PsiType.EMPTY_ARRAY), PsiTypeParameter.EMPTY_ARRAY, PsiSubstitutor.UNKNOWN);
      methodSignatures.add(signature);
    }

    return methodSignatures;
  }

  @Nullable
  public String getMethodReturnType(String moduleName, String className, String methodName, PsiType[] paramTypes) {
    final Element dynamicProperty = findConcreteDynamicMethod(getRootElement(moduleName), className, methodName, paramTypes);

    if (dynamicProperty == null) return null;

    return dynamicProperty.getAttributeValue(TYPE_ATTRIBUTE);
  }

  /*
  * Changes dynamic property type
  */

  public String replaceClassName(String moduleName, String oldClassName, String newClassName) {
    final Element rootElement = getRootElement(moduleName);
    final Element oldClassElement = findDynamicClassElement(rootElement, oldClassName);
    if (oldClassElement == null) return oldClassName;

    oldClassElement.setAttribute(CONTAINIG_CLASS_TYPE_ATTRIBUTE, newClassName);

    writeXMLTree(moduleName, rootElement);
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
  public Element findConcreteDynamicProperty(String moduleName, final String conatainingClassName, final String propertyName) {
    return findConcreteDynamicProperty(getRootElement(moduleName), conatainingClassName, propertyName);
  }

  @Nullable
  public Element findConcreteDynamicProperty(Element rootElement, final String conatainingClassName, final String propertyName) {
    Element definedClass = findDynamicClassElement(rootElement, conatainingClassName);
    if (definedClass == null) return null;

    final List definedPropertiesInTypeDef = definedClass.getContent(DynamicFiltersFactory.createConcretePropertyNameFilter(propertyName));
    if (definedPropertiesInTypeDef == null || definedPropertiesInTypeDef.size() == 0) {
      return null;
    }

    return ((Element) definedPropertiesInTypeDef.get(0));
  }

  @Nullable
  private Element findConcreteDynamicMethodWithName(Element rootElement, final String conatainingClassName, final String methodName) {
    Element definedClass = findDynamicClassElement(rootElement, conatainingClassName);
    if (definedClass == null) return null;

    final List definedPropertiesInTypeDef = definedClass.getContent(DynamicFiltersFactory.createConcreteMethodNameFilter(methodName));
    if (definedPropertiesInTypeDef == null || definedPropertiesInTypeDef.size() == 0) {
      return null;
    }

    return ((Element) definedPropertiesInTypeDef.get(0));
  }

  @Nullable
  private Element findDynamicClassElement(Element rootElement, final String conatainingClassName) {
    final List definedProperties = rootElement.getContent(DynamicFiltersFactory.createConcreteContainingClassTagFilter(conatainingClassName));

    if (definedProperties == null || definedProperties.size() == 0 || definedProperties.size() > 1) return null;
    return ((Element) definedProperties.get(0));
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
}