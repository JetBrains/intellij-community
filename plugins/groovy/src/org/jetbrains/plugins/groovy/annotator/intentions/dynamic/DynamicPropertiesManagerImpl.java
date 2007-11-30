package org.jetbrains.plugins.groovy.annotator.intentions.dynamic;

import com.intellij.ide.startup.StartupManagerEx;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupManager;
import com.intellij.openapi.util.JDOMUtil;
import org.jdom.*;
import org.jdom.filter.Filter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.DynamicProperty;
import org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.*;
import static org.jetbrains.plugins.groovy.annotator.intentions.dynamic.properties.elements.DynamicPropertyXMLElement.*;

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
    final Module module = ModuleManager.getInstance(getProject()).findModuleByName(dynamicProperty.getModuleName());

    final File moduleXML = myPathsToXmls.get(module);

    Document document;
    try {
      document = JDOMUtil.loadDocument(moduleXML);
    } catch (JDOMException e) {
      document = new Document();
    } catch (IOException e) {
      return null;
    }

    Element rootElement;
    if (document.hasRootElement()) {
      rootElement = document.getRootElement();
    } else {
      rootElement = new Element(DynamicPropertyXMLElement.PROPERTIES_TAG_NAME);
    }

    final String typeQualifiedName = dynamicProperty.getTypeQualifiedName();
    final List definedProperties = rootElement.getContent(new Filter() {
      public boolean matches(Object o) {
        if (!(o instanceof Element)) return false;
        if (!PROPERTY_TAG_NAME.equals(((Element) o).getQualifiedName())) return false;

        return typeQualifiedName.equals(((Element) o).getAttribute(QUALIFIED_TYPE_TAG_NAME).getValue());
      }
    });

    Element newRootElement;
    if (definedProperties != null && definedProperties.size() != 0) {
      if (definedProperties.size() > 1) return null;

      final Element definedTypeDef = ((Element) definedProperties.get(0));

      final List definedPropertiesInTypeDef = definedTypeDef.getContent(new Filter() {
        public boolean matches(Object o) {
          if (!(o instanceof Element)) return false;
          if (!NAME_PROPERTY_TAG_NAME.equals(((Element) o).getQualifiedName())) return false;

          return dynamicProperty.getPropertyName().equals(((Element) o).getText());
        }
      });

      if (definedPropertiesInTypeDef == null || (definedPropertiesInTypeDef != null && definedPropertiesInTypeDef.size() == 0)) {
        definedTypeDef.addContent(new DynamicPropertyNameElement(dynamicProperty.getPropertyName()));
      }

      newRootElement = rootElement;
    } else {
      rootElement.addContent(new DynamicPropertyXMLElementBase(dynamicProperty));
      newRootElement = rootElement;
    }

    FileWriter writer = null;
    try {
      writer = new FileWriter(moduleXML);
      JDOMUtil.writeElement(newRootElement, writer, "\n");
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

  @Nullable
  public DynamicProperty removeDynamicProperty(DynamicProperty dynamicProperty) {
    return null;
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