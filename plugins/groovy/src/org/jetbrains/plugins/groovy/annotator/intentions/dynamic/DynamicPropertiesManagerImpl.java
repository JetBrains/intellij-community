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

    final String dynPropertyElement = findConcreateDynamicProperty(rootElement, dynamicProperty.getTypeQualifiedName(), dynamicProperty.getPropertyName());

    if (dynPropertyElement == null) {
      final Element dynPropertyTypeElement = findDynamicPropertyTypeElement(rootElement, dynamicProperty.getTypeQualifiedName());

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
  public String findConcreateDynamicProperty(String moduleName, final String typeQualifiedName, final String propertyName) {
    Document document = loadModuleDynXML(moduleName);

    return findConcreateDynamicProperty(document.getRootElement(), typeQualifiedName, propertyName);
  }

  @Nullable
  public String findConcreateDynamicProperty(DynamicProperty dynamicProperty) {
    return findConcreateDynamicProperty(dynamicProperty.getModuleName(), dynamicProperty.getTypeQualifiedName(), dynamicProperty.getPropertyName());
  }

  @Nullable
  public String[] findDynamicPropertiesForType(String moduleName, String typeQualifiedName) {
    Document document = loadModuleDynXML(moduleName);

    final Element dynPropTypeElement = findDynamicPropertyTypeElement(document.getRootElement(), typeQualifiedName);
    if (dynPropTypeElement == null) return new String[0];

    final List propertiesOfType = dynPropTypeElement.getContent(DynamicPropertyXMLElement.createPropertyNameTagFilter());
    List<String> result = new ArrayList<String>();
    for (Object o : propertiesOfType) {
      result.add(((Element) o).getText());
    }

    return result.toArray(new String[0]);
  }

  @Nullable
  private Element findDynamicPropertyTypeElement(Element rootElement, final String typeQualifiedName) {
    final List definedProperties = rootElement.getContent(DynamicPropertyXMLElement.createConcreatePropertyTagFilter(typeQualifiedName));

    if (definedProperties == null || definedProperties.size() == 0 || definedProperties.size() > 1) return null;
    return ((Element) definedProperties.get(0));
  }

  @Nullable
  private String findConcreateDynamicProperty(Element rootElement, final String typeQualifiedName, final String propertyName) {
    Element definedTypeDef = findDynamicPropertyTypeElement(rootElement, typeQualifiedName);
    if (definedTypeDef == null) return null;

    final List definedPropertiesInTypeDef = definedTypeDef.getContent(DynamicPropertyXMLElement.createConcreatePropertyNameTagFilter(propertyName));
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