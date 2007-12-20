package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.diagnostic.Logger;
import org.jdom.*;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicProperty;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.*;

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
    final String moduleDynPath = findOrCreateProjectDynamicDir() + File.separatorChar +
        DYNAMIC_PROPERTIES_MODULE + "_" + module.getName() + "_" +
        module.getModuleFilePath().hashCode() + ".xml";

    return new File(findOrCreateFile(moduleDynPath));
  }

  @NotNull
  private String findOrCreateProjectDynamicDir() {
    final String url = getProject().getPresentableUrl();
    final String projectDynPath = findOrCreateDynamicDirectory() + File.separatorChar +
        DYNAMIC_PROPERTIES_PROJECT + "_" + getProject().getName() + "_" +
        (url != null ? url : getProject().getName()).hashCode();

    return findOrCreateDir(projectDynPath);
  }

  private String findOrCreateDynamicDirectory() {
    final String dynDirPath = PathManager.getSystemPath() + File.separatorChar + DYNAMIC_PROPERTIES_DIR;
    return findOrCreateDir(dynDirPath);
  }

  private String findOrCreateFile(String dynDirPath) {
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

  private String findOrCreateDir(String dynDirPath) {
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
  public DynamicProperty addDynamicProperty(final DynamicProperty dynamicProperty) {
    final String moduleName = dynamicProperty.getModuleName();

    Document document = loadModuleDynXML(moduleName);
    Element rootElement = document.getRootElement();

    final String dynPropertyElement = findConcreateDynamicProperty(rootElement, dynamicProperty.getContainingClassQualifiedName(), dynamicProperty.getPropertyName());

    if (dynPropertyElement == null) {
      final Element dynPropertyTypeElement = findDynamicPropertyClassElement(rootElement, dynamicProperty.getContainingClassQualifiedName());

      if (dynPropertyTypeElement == null) {
        rootElement.addContent(new DPContainigClassElement(dynamicProperty));
      } else {
        dynPropertyTypeElement.addContent(new DPPropertyElement(dynamicProperty));
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

    return dynamicProperty;
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
  public DynamicProperty removeDynamicProperty(DynamicProperty dynamicProperty) {
    return null;
  }

  @Nullable
  public String findConcreateDynamicProperty(String moduleName, final String typeQualifiedName, final String propertyName) {
    Document document = loadModuleDynXML(moduleName);

    return findConcreateDynamicProperty(document.getRootElement(), typeQualifiedName, propertyName);
  }
    
  @Nullable
  public String[] findDynamicPropertiesOfClass(String moduleName, String className) {
    Document document = loadModuleDynXML(moduleName);

    final Element dynPropTypeElement = findDynamicPropertyClassElement(document.getRootElement(), className);
    if (dynPropTypeElement == null) return new String[0];

    final List propertiesOfClass = dynPropTypeElement.getContent(DPFiltersFactory.createPropertyNameTagFilter());
    List<String> result = new ArrayList<String>();
    for (Object o : propertiesOfClass) {
      result.add(((Element) o).getText());
    }

    return result.toArray(new String[0]);
  }

  @Nullable
  private Element findDynamicPropertyClassElement(Element rootElement, final String typeQualifiedName) {
    final List definedProperties = rootElement.getContent(DPFiltersFactory.createConcreatePropertyTagFilter(typeQualifiedName));

    if (definedProperties == null || definedProperties.size() == 0 || definedProperties.size() > 1) return null;
    return ((Element) definedProperties.get(0));
  }

  @Nullable
  private String findConcreateDynamicProperty(Element rootElement, final String typeQualifiedName, final String propertyName) {
    Element definedClass = findDynamicPropertyClassElement(rootElement, typeQualifiedName);
    if (definedClass == null) return null;

    final List definedPropertiesInTypeDef = definedClass.getContent(DPFiltersFactory.createConcreatePropertyNameTagFilter(propertyName));
    if (definedPropertiesInTypeDef == null || definedPropertiesInTypeDef.size() == 0) {
      return null;
    }

    return definedPropertiesInTypeDef.get(0).toString();
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