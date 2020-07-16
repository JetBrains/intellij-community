// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.lang.ant.AntBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.io.JarUtil;
import com.intellij.util.config.*;
import com.intellij.util.containers.Convertor;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.net.MalformedURLException;
import java.util.Comparator;
import java.util.Properties;

public class AntInstallation {
  private static final Logger LOG = Logger.getInstance(AntInstallation.class);
  public static final StringProperty HOME_DIR = new StringProperty("homeDir", "");
  public static final AbstractProperty<String> NAME = new StringProperty("name", "");
  public static final ListProperty<AntClasspathEntry> CLASS_PATH = ListProperty.create("classpath");
  public static final Comparator<AntInstallation> NAME_COMPARATOR =
    (antInstallation, antInstallation1) -> String.CASE_INSENSITIVE_ORDER.compare(antInstallation.getName(), antInstallation1.getName());

  public static final Convertor<AntInstallation, AntReference> REFERENCE_TO_ANT = antInstallation -> antInstallation.getReference();
  public static final AbstractProperty<String> VERSION =
    new StringProperty("version", AntBundle.message("ant.unknown.version.string.presentation"));
  @NonNls private static final String PROPERTY_VERSION = "VERSION";

  private final ClassLoaderHolder myClassLoaderHolder;
  @NonNls public static final String PATH_TO_ANT_JAR = "lib/ant.jar";
  @NonNls public static final String LIB_DIR = "lib";
  @NonNls public static final String ANT_JAR_FILE = "ant.jar";
  @NonNls public static final String VERSION_RESOURCE = "org/apache/tools/ant/version.txt";

  public AntReference getReference() {
    return new AntReference.BindedReference(this);
  }

  public static final Externalizer<AntInstallation> EXTERNALIZER = new Externalizer<AntInstallation>() {
    @Override
    public AntInstallation readValue(Element dataElement) {
      AntInstallation antInstallation = new AntInstallation();
      antInstallation.readExternal(dataElement);
      return antInstallation;
    }

    @Override
    public void writeValue(Element dataElement, AntInstallation antInstallation) {
      antInstallation.myProperties.writeExternal(dataElement);
    }
  };

  private void readExternal(Element dataElement) {
    myProperties.readExternal(dataElement);
    File antJar = new File(HOME_DIR.get(myProperties), PATH_TO_ANT_JAR);
    updateVersion(antJar);
  }

  void updateVersion(File antJar) {
    if (antJar.exists()) {
      try {
        Properties antProps = loadProperties(antJar);
        VERSION.set(getProperties(), antProps.getProperty(PROPERTY_VERSION));
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
  }

  private final ExternalizablePropertyContainer myProperties;

  AntInstallation() {
    this(new ExternalizablePropertyContainer());
    registerProperties(myProperties);
  }

  private AntInstallation(final ExternalizablePropertyContainer properties) {
    myProperties = properties;
    myClassLoaderHolder = new AntInstallationClassLoaderHolder(myProperties);
  }

  public String getName() {
    return NAME.get(myProperties);
  }

  public void setName(final String name) {
    NAME.set(myProperties, name);
  }

  public String getVersion() {
    return VERSION.get(myProperties);
  }

  public String getHomeDir() {
    return HOME_DIR.get(myProperties);
  }

  public AbstractProperty.AbstractPropertyContainer getProperties() {
    return myProperties;
  }

  @NotNull
  public ClassLoader getClassLoader() {
    return myClassLoaderHolder.getClassloader();
  }

  public void updateClasspath() {
    myClassLoaderHolder.updateClasspath();
  }

  public static AntInstallation fromHome(String homePath) throws ConfigurationException {
    File antHome = new File(homePath);
    String antPath = "'" + antHome.getAbsolutePath() + "'";
    checkExists(antHome, AntBundle.message("path.to.ant.does.not.exist.error.message", antPath));
    File lib = new File(antHome, LIB_DIR);
    checkExists(lib, AntBundle.message("lib.directory.not.found.in.ant.path.error.message", antPath));
    File antJar = new File(lib, ANT_JAR_FILE);
    checkExists(antJar, AntBundle.message("ant.jar.not.found.in.directory.error.message", lib.getAbsolutePath()));
    if (antJar.isDirectory()) {
      throw new ConfigurationException(AntBundle.message("ant.jar.is.directory.error.message", antJar.getAbsolutePath()));
    }
    try {
      Properties properties = loadProperties(antJar);
      AntInstallation antInstallation = new AntInstallation();
      HOME_DIR.set(antInstallation.getProperties(), antHome.getAbsolutePath());
      final String versionProp = properties.getProperty(PROPERTY_VERSION);
      NAME.set(antInstallation.getProperties(), AntBundle.message("apache.ant.with.version.string.presentation", versionProp));
      VERSION.set(antInstallation.getProperties(), versionProp);
      antInstallation.addClasspathEntry(new AllJarsUnderDirEntry(lib));
      return antInstallation;
    }
    catch (MalformedURLException e) {
      LOG.error(e);
      return null;
    }
  }

  private static Properties loadProperties(File antJar) throws MalformedURLException, ConfigurationException {
    Properties properties = JarUtil.loadProperties(antJar, VERSION_RESOURCE);
    if (properties == null) {
      throw new ConfigurationException(AntBundle.message("cant.read.from.ant.jar.error.message", antJar.getAbsolutePath()));
    }
    return properties;
  }

  private void addClasspathEntry(AntClasspathEntry entry) {
    CLASS_PATH.getModifiableList(getProperties()).add(entry);
  }

  private static void checkExists(File file, String message) throws ConfigurationException {
    if (!file.exists()) throw new ConfigurationException(message);
  }

  public static class ConfigurationException extends Exception {
    public ConfigurationException(String message) {
      super(message);
    }
  }

  private static void registerProperties(ExternalizablePropertyContainer container) {
    container.registerProperty((StringProperty)NAME, Externalizer.STRING);
    container.registerProperty(HOME_DIR, Externalizer.STRING);
    container.registerProperty(CLASS_PATH, "classpathItem", AntClasspathEntry.EXTERNALIZER);
    container.registerProperty((StringProperty)VERSION, Externalizer.STRING);
  }
}
