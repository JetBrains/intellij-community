/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package org.jetbrains.jps.eclipse.model;

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.HashSet;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.eclipse.*;
import org.jetbrains.jps.model.JpsElementFactory;
import org.jetbrains.jps.model.java.*;
import org.jetbrains.jps.model.library.JpsLibrary;
import org.jetbrains.jps.model.library.JpsLibraryReference;
import org.jetbrains.jps.model.library.JpsOrderRootType;
import org.jetbrains.jps.model.module.*;
import org.jetbrains.jps.model.serialization.JpsMacroExpander;
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.*;

/**
 * User: anna
 * Date: 10/29/12
 */
class JpsEclipseClasspathReader extends AbstractEclipseClasspathReader<JpsModule> {
  private static final Logger LOG = Logger.getInstance(JpsEclipseClasspathReader.class);
  private final Map<String, String> myLibLevels;

  public JpsEclipseClasspathReader(String rootPath,
                                   @Nullable List<String> currentRoots,
                                   @Nullable Set<String> moduleNames,
                                   Map<String, String> levels) {
    super(rootPath, currentRoots, moduleNames);
    myLibLevels = levels;
  }

  @Override
  protected String prepareValidUrlInsideJar(String url) {
    //strip path inside jar
    final String jarSeparator = "!/";
    final int localPathEndIdx = url.indexOf(jarSeparator);
    if (localPathEndIdx > -1) {
      return url.substring(0, localPathEndIdx + jarSeparator.length());
    }
    return url;
  }

  @Override
  protected void addNamedLibrary(JpsModule rootModel,
                                 Collection<String> unknownLibraries,
                                 boolean exported,
                                 String name,
                                 boolean applicationLevel) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("loading " + rootModel.getName() + ": adding " + (applicationLevel ? "application" : "project") + " library '" + name + "'");
    }
    JpsElementFactory factory = JpsElementFactory.getInstance();
    JpsLibraryReference libraryReference;
    final String level = myLibLevels.get(name);
    libraryReference = level != null ? factory.createLibraryReference(name, JpsLibraryTableSerializer.createLibraryTableReference(level))
                                     : factory.createLibraryReference(name, factory.createGlobalReference());
    final JpsLibraryDependency dependency = rootModel.getDependenciesList().addLibraryDependency(libraryReference);
    setLibraryEntryExported(dependency, exported);
  }

  @Override
  protected void addInvalidModuleEntry(JpsModule rootModel, boolean exported, String moduleName) {
    final JpsElementFactory elementFactory = JpsElementFactory.getInstance();
    final JpsDependenciesList dependenciesList = rootModel.getDependenciesList();
    final JpsModuleDependency dependency = dependenciesList.addModuleDependency(elementFactory.createModuleReference(moduleName));
    final JpsJavaDependencyExtension extension = getService().getOrCreateDependencyExtension(dependency);
    extension.setExported(exported);
  }

  @Override
  protected void setUpModuleJdk(JpsModule rootModel,
                                Collection<String> unknownJdks,
                                EclipseModuleManager eclipseModuleManager,
                                String jdkName) {
    if (LOG.isDebugEnabled()) {
      LOG.debug("loading " + rootModel.getName() + ": set module jdk " + jdkName);
    }
    rootModel.getDependenciesList().addSdkDependency(JpsJavaSdkType.INSTANCE);
  }

  @Override
  protected void addSourceFolder(JpsModule rootModel, String srcUrl, boolean testFolder) {
    rootModel.addSourceRoot(srcUrl, testFolder ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
  }

  @Override
  protected void addSourceFolderToCurrentContentRoot(JpsModule rootModel, String srcUrl, boolean testFolder) {
    rootModel.addSourceRoot(srcUrl, testFolder ? JavaSourceRootType.TEST_SOURCE : JavaSourceRootType.SOURCE);
  }

  @Override
  protected void addJUnitDefaultLib(JpsModule rootModel, String junitName, ExpandMacroToPathMap macroMap) {
    final String ideaHome = macroMap.substitute("$APPLICATION_HOME_DIR$", SystemInfo.isFileSystemCaseSensitive);
    final FilenameFilter junitFilter = new FilenameFilter() {
      @Override
      public boolean accept(File dir, String name) {
        return name.startsWith("junit");
      }
    };
    File[] junitJars = new File(ideaHome, "lib").listFiles(junitFilter);
    if (junitJars == null || junitJars.length == 0) {
      junitJars = new File(new File(ideaHome, "community"), "lib").listFiles(junitFilter);
    }
    if (junitJars != null && junitJars.length > 0) {
      final boolean isJUnit4 = junitName.contains("4");
      File junitJar = null;
      for (File jar : junitJars) {
        final boolean isCurrentJarV4 = jar.getName().contains("4");
        if (isCurrentJarV4 && isJUnit4 || !isCurrentJarV4 && !isJUnit4) {
          junitJar = jar;
          break;
        }
      }
      if (junitJar != null) {
        final JpsLibrary jpsLibrary = rootModel.addModuleLibrary(junitName, JpsJavaLibraryType.INSTANCE);
        jpsLibrary.addRoot(pathToUrl(junitJar.getPath()), JpsOrderRootType.COMPILED);
        final JpsDependenciesList dependenciesList = rootModel.getDependenciesList();
        dependenciesList.addLibraryDependency(jpsLibrary);
      }
    }
  }

  @Override
  protected void addModuleLibrary(JpsModule rootModel,
                                  Element element,
                                  boolean exported,
                                  String libName,
                                  String url,
                                  String srcUrl,
                                  String nativeRoot, 
                                  ExpandMacroToPathMap macroMap) {
    final JpsLibrary jpsLibrary = rootModel.addModuleLibrary(libName, JpsJavaLibraryType.INSTANCE);
    final JpsDependenciesList dependenciesList = rootModel.getDependenciesList();
    final JpsLibraryDependency dependency = dependenciesList.addLibraryDependency(jpsLibrary);
    url = StringUtil.trimStart(url, "file://");
    final String linked = expandLinkedResourcesPath(url, macroMap);
    if (linked != null) {
      url = pathToUrl(linked);
    }
    else {
      url = expandEclipsePath2Url(rootModel, url);
    }
    LOG.debug("loading " + rootModel.getName() + ": adding module library " + libName + ": " + url);
    jpsLibrary.addRoot(url, JpsOrderRootType.COMPILED);

    setLibraryEntryExported(dependency, exported);
  }

  @Override
  protected String expandEclipsePath2Url(JpsModule rootModel, String path) {

    String url = null;
    if (new File(path).exists()) {  //absolute path
      url = pathToUrl(path);
    }
    else {
      final String relativePath = new File(myRootPath, path).getPath(); //inside current project
      final File file = new File(relativePath);
      if (file.exists()) {
        url = pathToUrl(relativePath);
      }
      else if (path.startsWith("/")) { //relative to other project
        final String moduleName = EPathCommonUtil.getRelativeModuleName(path);
        final String relativeToRootPath = EPathCommonUtil.getRelativeToModulePath(path);
        url = EPathCommonUtil.expandEclipseRelative2ContentRoots(myCurrentRoots, moduleName, relativeToRootPath);
      }
    }
    if (url == null) {
      url = pathToUrl(path);
    }

    return prepareValidUrlInsideJar(url);
  }

  @Override
  protected Set<String> getDefinedCons() {
    return Collections.emptySet();
  }


  @Override
  protected int rearrange(JpsModule rootModel) {
    return 0;
  }

  public void readClasspath(JpsModule model,
                            final String testPattern,
                            Element classpathElement, JpsMacroExpander expander) throws IOException {
    LOG.debug("start loading classpath for " + model.getName());
    final HashSet<String> libs = new HashSet<String>();
    for (Object o : classpathElement.getChildren(EclipseXml.CLASSPATHENTRY_TAG)) {
      try {
        readClasspathEntry(model, new ArrayList<String>(), new ArrayList<String>(), new HashSet<String>(),
                           testPattern, (Element)o, 0, null, expander.getExpandMacroMap(), libs);
      }
      catch (ConversionException e) {
        throw new IOException(e);
      }
    }
    boolean foundSdkDependency = false;
    JpsDependenciesList dependenciesList = model.getDependenciesList();
    for (JpsDependencyElement element : dependenciesList.getDependencies()) {
      if (element instanceof JpsSdkDependency) {
        foundSdkDependency = true;
        break;
      }
    }
    if (!foundSdkDependency) {
      dependenciesList.addSdkDependency(JpsJavaSdkType.INSTANCE);
    }
    if (LOG.isDebugEnabled()) {
      String name = model.getName();
      LOG.debug("finished loading classpath for " + name + " (" + dependenciesList.getDependencies().size() + " items):");
      for (JpsDependencyElement element : dependenciesList.getDependencies()) {
        LOG.debug(" [" + name + "]:" + element.toString());
      }
    }
  }

  @Nullable
  private String expandLinkedResourcesPath(final String path, ExpandMacroToPathMap expander) {
    final EclipseProjectFinder.LinkedResource linkedResource = EclipseProjectFinder.findLinkedResource(myRootPath, path);
    if (linkedResource != null) {
      if (linkedResource.containsPathVariable()) {
        final String toPathVariableFormat =
          getVariableRelatedPath(linkedResource.getVariableName(), linkedResource.getRelativeToVariablePath());
        return expander.substitute(toPathVariableFormat, SystemInfo.isFileSystemCaseSensitive);
      }
      return linkedResource.getLocation();
    }
    return null;
  }

  public void setupOutput(JpsModule rootModel, final String path) {
    final JpsJavaModuleExtension extension = getService().getOrCreateModuleExtension(rootModel);
    String outputUrl = pathToUrl(path);
    extension.setOutputUrl(outputUrl);
    extension.setTestOutputUrl(outputUrl);
    //extension.setInheritOutput(false);

    rootModel.getDependenciesList().addModuleSourceDependency();
  }

  private static void setLibraryEntryExported(final JpsDependencyElement dependency, boolean exported) {
    final JpsJavaDependencyExtension extension = getService().getOrCreateDependencyExtension(dependency);
    extension.setExported(exported);
  }

  private static JpsJavaExtensionService getService() {
    return JpsJavaExtensionService.getInstance();
  }
}
