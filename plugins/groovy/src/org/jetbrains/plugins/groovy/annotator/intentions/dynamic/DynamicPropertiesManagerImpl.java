package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassType;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.real.DynamicPropertyReal;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.*;
import static org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DPElement.*;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.virtual.DynamicPropertyVirtual;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.impl.statements.expressions.TypesUtil;

import java.io.*;
import java.util.*;

/**
 * User: Dmitry.Krasilschikov
 * Date: 23.11.2007
 */
public class DynamicPropertiesManagerImpl extends DynamicPropertiesManager {
  private static final Logger LOG = Logger.getInstance("org.jetbrains.plugins.groovy.annotator.intentions.dynamic.DynamicPropertiesManagerImpl");

  private final Project myProject;
  private Map<Module, File> myPathsToXmls;
  private List<DynamicPropertyChangeListener> myListeners = new ArrayList<DynamicPropertyChangeListener>();

  public DynamicPropertiesManagerImpl(Project project) {
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
  public DynamicPropertyVirtual addDynamicProperty(final DynamicPropertyVirtual dynamicPropertyVirtual) {
    final String moduleName = dynamicPropertyVirtual.getModuleName();

    Document document = loadModuleDynXML(moduleName);
    Element rootElement = document.getRootElement();

    final Element propertyElement = findConcreateDynamicProperty(rootElement, dynamicPropertyVirtual.getContainingClassQualifiedName(), dynamicPropertyVirtual.getPropertyName());

    if (propertyElement == null) {
      final Element dynPropertyTypeElement = findDynamicPropertyClassElement(rootElement, dynamicPropertyVirtual.getContainingClassQualifiedName());

      if (dynPropertyTypeElement == null) {
        rootElement.addContent(new DPContainingClassElement(dynamicPropertyVirtual));
      } else {
        dynPropertyTypeElement.addContent(new DPPropertyElement(dynamicPropertyVirtual));
      }
    }

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

    fireChange();

    return dynamicPropertyVirtual;
  }

  private Document loadModuleDynXML(String moduleName) {
    Document document;
    try {
      document = JDOMUtil.loadDocument(myPathsToXmls.get(ModuleManager.getInstance(getProject()).findModuleByName(moduleName)));
    } catch (JDOMException e) {
      document = new Document();
    } catch (IOException e) {
      document = new Document();
    }

    if (!document.hasRootElement()) {
      document.setRootElement(new DPPropertiesElement());
    }
    return document;
  }

  @Nullable
  public DynamicPropertyReal removeDynamicProperty(DynamicPropertyReal dynamicPropertyReal) {
    return null;
  }

  @Nullable
  public Element findConcreateDynamicProperty(GrReferenceExpression referenceExpression, final String moduleName, final String containingClassName, final String propertyName) {
    final PsiClassType type = TypesUtil.createPsiClassTypeFromText(referenceExpression, containingClassName);

    final PsiClass psiClass = type.resolve();
    if (psiClass == null) return null;

    final Set<PsiClass> classes = new HashSet<PsiClass>();
    classes.addAll(Arrays.asList(psiClass.getSupers()));

    Element result = findConcreateDynamicPropertyWithSupers(moduleName, psiClass.getQualifiedName(), propertyName);
    if (result != null) return result;

    for (PsiClass aClass : classes) {
      result = findConcreateDynamicPropertyWithSupers(moduleName, aClass.getQualifiedName(), propertyName);

      if (result != null) return result;
    }
    return null;
  }

  @Nullable
  public Element findConcreateDynamicPropertyWithSupers(String moduleName, final String conatainingClassName, final String propertyName) {
    Document document = loadModuleDynXML(moduleName);

    return findConcreateDynamicProperty(document.getRootElement(), conatainingClassName, propertyName);
  }

  @Nullable
  protected String getTypeOfDynamicProperty(GrReferenceExpression referenceExpression, String moduleName, String containingClassName, String propertyName) {
    if (containingClassName == null) return null;
    final Element dynamicProperty = findConcreateDynamicProperty(referenceExpression, moduleName, containingClassName, propertyName);
    if (dynamicProperty == null) return null;

    final List types = dynamicProperty.getContent(DynamicFiltersFactory.createPropertyTypeTagFilter());
    if (types == null || (types.size() != 1)) return null;

    final Object type = types.get(0);
    if(!(type instanceof Element)) return null;

    return ((Element) type).getText();
  }

  @NotNull
  public String[] findDynamicPropertiesOfClass(String moduleName, String className) {
    Document document = loadModuleDynXML(moduleName);

    final Element containingClassElement = findDynamicPropertyClassElement(document.getRootElement(), className);
    if (containingClassElement == null) return new String[0];

    final List propertiesTagsOfClass = containingClassElement.getContent(DynamicFiltersFactory.createPropertyTagFilter());

    List<String> result = new ArrayList<String>();
    for (Object o : propertiesTagsOfClass) {
      result.add(((Element) o).getAttributeValue(PROPERTY_NAME_ATTRIBUTE));
    }

    return result.toArray(new String[0]);
  }

  @NotNull
  public String findDynamicPropertyType(String moduleName, String className, String propertyName) {
    final Element dynamicProperty = findConcreateDynamicProperty(getRootElement(moduleName), className, propertyName);

    if (dynamicProperty == null) return "";

    final List types = dynamicProperty.getContent(DynamicFiltersFactory.createPropertyTypeTagFilter());

    if (types == null || types.isEmpty()) return "";
    final Object typeTag = types.get(0);

    if (typeTag == null || !(typeTag instanceof Element)) return "";

    return ((Element) typeTag).getText();
  }

  @NotNull
  public DynamicPropertyVirtual[] getAllDynamicProperties(String moduleName) {
    Set<DynamicPropertyVirtual> result = new HashSet<DynamicPropertyVirtual>();
    Document document = loadModuleDynXML(moduleName);

    final List definedContainingClasses = document.getRootElement().getContent(DynamicFiltersFactory.createContainingClassTagFilter());

    for (Object definedContainingClass : definedContainingClasses) {
      final Element containingClass = (Element) definedContainingClass;

      final String qualifiedName = containingClass.getAttributeValue(CONTAINIG_CLASS_TYPE_ATTRIBUTE);
      final List propertyTags = containingClass.getContent(DynamicFiltersFactory.createPropertyTagFilter());

      for (Object propertyTag : propertyTags) {
        final String propertyName = ((Element) propertyTag).getAttributeValue(PROPERTY_NAME_ATTRIBUTE);
        final String propertyType = findDynamicPropertyType(moduleName, qualifiedName, propertyName);
        result.add(new DynamicPropertyVirtual(propertyName, qualifiedName, moduleName, propertyType));
      }
    }

    return result.toArray(DynamicPropertyVirtual.EMPTY_ARRAY);
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

  public void fireChangeDynamicPropertyEnviroment() {
    //TODO this
    //change package
    //rename property
    // ... etc
  }

  public Element getRootElement(String moduleName) {
    return loadModuleDynXML(moduleName).getRootElement();
  }

  /*
  * Adds dynamicPropertyChange listener
  */
  public void addDynamicChangeListener(DynamicPropertyChangeListener listener) {
    myListeners.add(listener);
  }

  /*
  * Removes dynamicPropertyChange listener
  */
  public void removeDynamicChangeListener(DynamicPropertyChangeListener listener) {
    myListeners.remove(listener);
  }

  public void fireChange(){
    for (DynamicPropertyChangeListener listener : myListeners) {
      listener.dynamicPropertyChange();
    }
  }

  @Nullable
  private Element findConcreateDynamicProperty(Element rootElement, final String conatainingClassName, final String propertyName) {
    Element definedClass = findDynamicPropertyClassElement(rootElement, conatainingClassName);
    if (definedClass == null) return null;

    final List definedPropertiesInTypeDef = definedClass.getContent(DynamicFiltersFactory.createConcreatePropertyNameFilter(propertyName));
    if (definedPropertiesInTypeDef == null || definedPropertiesInTypeDef.size() == 0) {
      return null;
    }

    return ((Element) definedPropertiesInTypeDef.get(0));
  }

  @Nullable
  private Element findDynamicPropertyClassElement(Element rootElement, final String conatainingClassName) {
    final List definedProperties = rootElement.getContent(DynamicFiltersFactory.createConcreatePropertyTagFilter(conatainingClassName));

    if (definedProperties == null || definedProperties.size() == 0 || definedProperties.size() > 1) return null;
    return ((Element) definedProperties.get(0));
  }

  public void disposeComponent() {
  }

  @NotNull
  public String getComponentName() {
    return "DynamicPropertiesManagerImpl";
  }

  public void projectOpened() {
  }

  public void projectClosed() {
  }
}