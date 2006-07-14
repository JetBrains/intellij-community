package com.intellij.lang.ant.config.impl;

import com.intellij.ant.AntBundle;
import com.intellij.ide.macro.MacroManager;
import com.intellij.lang.ant.config.AntConfigurationBase;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.ProjectJdk;
import com.intellij.openapi.projectRoots.ProjectJdkTable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.config.*;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.ArrayList;
import java.util.Map;

public class GlobalAntConfiguration implements ApplicationComponent, JDOMExternalizable {
  private static final Logger LOG = Logger.getInstance("#com.intellij.lang.ant.config.impl.AntGlobalConfiguration");
  public static final StorageProperty FILTERS_TABLE_LAYOUT = new StorageProperty("filtersTableLayout");
  public static final StorageProperty PROPERTIES_TABLE_LAYOUT = new StorageProperty("propertiesTableLayout");
  static final ListProperty<AntInstallation> ANTS = ListProperty.create("registeredAnts");
  private final ExternalizablePropertyContainer myProperties = new ExternalizablePropertyContainer();
  private AntInstallation myBundledAnt;
  public static final String BUNDLED_ANT_NAME = AntBundle.message("ant.reference.bundled.ant.name");
  public final Condition<AntInstallation> IS_USER_ANT = new Condition<AntInstallation>() {
    public boolean value(AntInstallation antInstallation) {
      return antInstallation != myBundledAnt;
    }
  };

  public static final AbstractProperty<GlobalAntConfiguration> INSTANCE = new ValueProperty<GlobalAntConfiguration>(
    "$GlobalAntConfiguration.INSTANCE", null);
  @NonNls public static final String ANT_FILE = "ant";
  @NonNls public static final String LIB_DIR = "lib";
  @NonNls public static final String ANT_JAR_FILE_NAME = "ant.jar";

  public GlobalAntConfiguration() {
    myProperties.registerProperty(FILTERS_TABLE_LAYOUT);
    myProperties.registerProperty(PROPERTIES_TABLE_LAYOUT);
    myProperties.registerProperty(ANTS, ANT_FILE, AntInstallation.EXTERNALIZER);
    INSTANCE.set(myProperties, this);
    myProperties.rememberKey(INSTANCE);

    myBundledAnt = createBundledAnt();
  }

  @NotNull
  public String getComponentName() {
    return "GlobalAntConfiguration";
  }

  public void initComponent() { }

  public static AntInstallation createBundledAnt() {
    AntInstallation bundledAnt = new AntInstallation() {
      public AntReference getReference() {
        return AntReference.BUNDLED_ANT;
      }
    };
    AntInstallation.NAME.set(bundledAnt.getProperties(), BUNDLED_ANT_NAME);
    final File ideaLib = new File(PathManager.getLibPath());
    final File antHome = new File(ideaLib, ANT_FILE);
    AntInstallation.HOME_DIR.set(bundledAnt.getProperties(), antHome.getAbsolutePath());
    ArrayList<AntClasspathEntry> classpath = AntInstallation.CLASS_PATH.getModifiableList(bundledAnt.getProperties());
    File antLibDir = new File(antHome, LIB_DIR);
    classpath.add(new AllJarsUnderDirEntry(antLibDir));
    bundledAnt.updateVersion(new File(antLibDir, ANT_JAR_FILE_NAME));
    return bundledAnt;
  }

  public void disposeComponent() {}

  public void readExternal(Element element) throws InvalidDataException {
    myProperties.readExternal(element);
  }

  public void writeExternal(Element element) throws WriteExternalException {
    myProperties.writeExternal(element);
  }

  public static GlobalAntConfiguration getInstance() {
    return ApplicationManager.getApplication().getComponent(GlobalAntConfiguration.class);
  }

  public Map<AntReference, AntInstallation> getConfiguredAnts() {
    HashMap<AntReference, AntInstallation> map = ContainerUtil.assignKeys(ANTS.getIterator(getProperties()),
                                                                          AntInstallation.REFERENCE_TO_ANT);
    map.put(AntReference.BUNDLED_ANT, myBundledAnt);
    return map;
  }

  public AntInstallation getBundledAnt() {
    return myBundledAnt;
  }

  public AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  public AbstractProperty.AbstractPropertyContainer getProperties(Project project) {
    return new CompositePropertyContainer(new AbstractProperty.AbstractPropertyContainer[]{
      myProperties, AntConfigurationBase.getInstance(project).getProperties()});
  }

  public void addConfiguration(final AntInstallation ant) {
    if (getConfiguredAnts().containsKey(ant.getReference())) {
      LOG.error("Duplicated name: " + ant.getName());
    }
    ANTS.getModifiableList(getProperties()).add(ant);
  }

  public void removeConfiguration(final AntInstallation ant) {
    ANTS.getModifiableList(getProperties()).remove(ant);
  }

  public static ProjectJdk findJdk(final String jdkName) {
    return ProjectJdkTable.getInstance().findJdk(jdkName);
  }

  public static MacroManager getMacroManager() {
    return MacroManager.getInstance();
  }
}
