package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.JDOMUtil;
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
    final String projectDynPath = findOrCreateDynamicDirectory() + File.separatorChar +
        DYNAMIC_PROPERTIES_PROJECT + "_" + getProject().getName() + "_" +
        getProject().getPresentableUrl().hashCode();

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

    final Element dynPropertyElement = findDynamicProperty(rootElement, dynamicProperty.getTypeQualifiedName(), dynamicProperty.getPropertyName());

    if (dynPropertyElement == null) {
      final Element dynPropertyTypeElement = findDynamicPropertyType(rootElement, dynamicProperty.getTypeQualifiedName());

      if (dynPropertyTypeElement == null) {
        rootElement.addContent(new DynamicPropertyXMLElementBase(dynamicProperty));
      } else {
        dynPropertyTypeElement.addContent(new DynamicPropertyNameElement(dynamicProperty.getPropertyName()));
      }
    }

    FileWriter writer = null;
    try {
      writer = new FileWriter(myPathsToXmls.get(ModuleManager.getInstance(getProject()).findModuleByName(moduleName)));
      JDOMUtil.writeElement(rootElement, writer, "\n");
    } catch (IOException e) {
    } finally {
      try {
        if (writer != null) {
          writer.flush();
          writer.close();
        }
      } catch (IOException e) {
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
      document.setRootElement(new Element(DynamicPropertyXMLElement.PROPERTIES_TAG_NAME));
    }
    return document;
  }

  @Nullable
  public DynamicProperty removeDynamicProperty(DynamicProperty dynamicProperty) {
    return null;
  }

  @Nullable
  public Element findDynamicProperty(String moduleName, final String typeQualifiedName, final String propertyName) {
    Document document = loadModuleDynXML(moduleName);

    return findDynamicProperty(document.getRootElement(), typeQualifiedName, propertyName);
  }

  @Nullable
  public Element findDynamicProperty(DynamicProperty dynamicProperty) {
    return findDynamicProperty(dynamicProperty.getModuleName(), dynamicProperty.getTypeQualifiedName(), dynamicProperty.getPropertyName());
  }

  @Nullable
  public Element findDynamicPropertyType(Element rootElement, final String typeQualifiedName) {
    final List definedProperties = rootElement.getContent(DynamicPropertyXMLElement.createConcreatePropertyTagFilter(typeQualifiedName));

    if (definedProperties == null || definedProperties.size() == 0 || definedProperties.size() > 1) return null;
    return ((Element) definedProperties.get(0));
  }

  @Nullable
  private Element findDynamicProperty(Element rootElement, final String typeQualifiedName, final String propertyName) {
    Element definedTypeDef = findDynamicPropertyType(rootElement, typeQualifiedName);
    if (definedTypeDef == null) return null;

    final List definedPropertiesInTypeDef = definedTypeDef.getContent(DynamicPropertyXMLElement.createConcreatePropertyNameTagFilter(propertyName));
    if (definedPropertiesInTypeDef == null || definedPropertiesInTypeDef.size() == 0) {
      return null;
    }

    return (Element) definedPropertiesInTypeDef.get(0);
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