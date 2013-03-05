/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.eclipse;

import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.util.*;
import java.util.jar.Manifest;
import java.util.regex.PatternSyntaxException;

/**
 * User: anna
 * Date: 10/29/12
 */
public abstract class AbstractEclipseClasspathReader<T> {
  protected final String myRootPath;
  @Nullable protected final List<String> myCurrentRoots;
  @Nullable protected final Set<String> myModuleNames;

  public AbstractEclipseClasspathReader(final String rootPath,
                                        @Nullable List<String> currentRoots, @Nullable Set<String> moduleNames) {
    myRootPath = FileUtil.toSystemIndependentName(rootPath);
    myCurrentRoots = currentRoots;
    myModuleNames = moduleNames;
  }

  protected abstract String prepareValidUrlInsideJar(String url);
  protected abstract void addNamedLibrary(T rootModel,
                                          Collection<String> unknownLibraries,
                                          boolean exported,
                                          String name,
                                          boolean applicationLevel);
  protected abstract void addInvalidModuleEntry(T rootModel, boolean exported, String moduleName);
  protected abstract void setUpModuleJdk(T rootModel,
                                         Collection<String> unknownJdks,
                                         EclipseModuleManager eclipseModuleManager,
                                         String jdkName);
  public abstract void setupOutput(T rootModel, String path);
  protected abstract void addSourceFolder(T rootModel, String srcUrl, boolean testFolder);
  protected abstract void addSourceFolderToCurrentContentRoot(T rootModel, String srcUrl, boolean testFolder);

  protected abstract void addJUnitDefaultLib(T rootModel, String junitName, ExpandMacroToPathMap macroMap);
  protected abstract void addModuleLibrary(T rootModel,
                                           Element element,
                                           boolean exported,
                                           String libName,
                                           String url,
                                           String srcUrl, ExpandMacroToPathMap macroMap);
  protected abstract String expandEclipsePath2Url(T rootModel, String path);

  protected abstract Set<String> getDefinedCons();

  protected abstract int rearrange(T rootModel);

  protected void readClasspathEntry(T rootModel,
                                    final Collection<String> unknownLibraries,
                                    Collection<String> unknownJdks,
                                    final Set<String> usedVariables,
                                    Set<String> refsToModules,
                                    final String testPattern,
                                    Element element, int idx,
                                    final EclipseModuleManager eclipseModuleManager,
                                    final ExpandMacroToPathMap macroMap, 
                                    final Set<String> libs) throws ConversionException {
    String kind = element.getAttributeValue(EclipseXml.KIND_ATTR);
    if (kind == null) {
      throw new ConversionException("Missing classpathentry/@kind");
    }


    String path = element.getAttributeValue(EclipseXml.PATH_ATTR);
    if (path == null) {
      throw new ConversionException("Missing classpathentry/@path");
    }

    final boolean exported = EclipseXml.TRUE_VALUE.equals(element.getAttributeValue(EclipseXml.EXPORTED_ATTR));

    if (kind.equals(EclipseXml.SRC_KIND)) {
      if (path.startsWith("/")) {
        final String moduleName = path.substring(1);
        refsToModules.add(moduleName);
        addInvalidModuleEntry(rootModel, exported, moduleName);
      }
      else {
        String srcUrl = pathToUrl(myRootPath + "/" + path);
        boolean isTestFolder = false;
        try {
          isTestFolder = testPattern != null && testPattern.length() > 0 && path.matches(testPattern);
        }
        catch (PatternSyntaxException e) {
          isTestFolder = false;
        }
        final String linked = expandLinkedResourcesPath(macroMap, usedVariables, path);
        if (linked != null) {
          srcUrl = prepareValidUrlInsideJar(pathToUrl(linked));
          eclipseModuleManager.registerEclipseLinkedSrcVarPath(srcUrl, path);
          addSourceFolder(rootModel, srcUrl, isTestFolder);
        }
        else {
          addSourceFolderToCurrentContentRoot(rootModel, srcUrl, isTestFolder);
        }
        eclipseModuleManager.setExpectedModuleSourcePlace(rearrange(rootModel));
        eclipseModuleManager.registerSrcPlace(srcUrl, idx);
      }
    }

    else if (kind.equals(EclipseXml.OUTPUT_KIND)) {
      String output = myRootPath + "/" + path;
      final String linked = expandLinkedResourcesPath(macroMap, usedVariables, path);
      if (linked != null) {
        output = linked;
        eclipseModuleManager.registerEclipseLinkedVarPath(pathToUrl(output), path);
      }
      setupOutput(rootModel, output);
    }

    else if (kind.equals(EclipseXml.LIB_KIND)) {
      final String libName = getPresentableName(path, libs);


      final String linked = expandLinkedResourcesPath(macroMap, usedVariables, path);
      final String url;
      if (linked != null) {
        url = prepareValidUrlInsideJar(pathToUrl(linked));
        eclipseModuleManager.registerEclipseLinkedVarPath(url, path);
      }
      else {
        url = expandEclipsePath2Url(rootModel, path);
      }

      eclipseModuleManager.registerEclipseLibUrl(url);

      final String sourcePath = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      String srcUrl = null;
      if (sourcePath != null) {
        final String linkedSrc = expandLinkedResourcesPath(macroMap, usedVariables, sourcePath);
        if (linkedSrc != null) {
          srcUrl = prepareValidUrlInsideJar(pathToUrl(linkedSrc));
          eclipseModuleManager.registerEclipseLinkedSrcVarPath(srcUrl, sourcePath);
        }
        else {
          srcUrl = expandEclipsePath2Url(rootModel, sourcePath);
        }
      }

      addModuleLibrary(rootModel, element, exported, libName, url, srcUrl, macroMap);
    }
    else if (kind.equals(EclipseXml.VAR_KIND)) {
      int slash = path.indexOf("/");
      if (slash == 0) {
        throw new ConversionException("Incorrect 'classpathentry/var@path' format");
      }

      final String libName = getPresentableName(path, libs);

      final String url = eclipseVariabledPath2Url(macroMap, usedVariables, path, 0);
      eclipseModuleManager.registerEclipseVariablePath(url, path);
      final String srcPathAttr = element.getAttributeValue(EclipseXml.SOURCEPATH_ATTR);
      String srcUrl = null;
      if (srcPathAttr != null) {
        srcUrl = eclipseVariabledPath2Url(macroMap, usedVariables, srcPathAttr, srcVarStart(srcPathAttr));
        eclipseModuleManager.registerEclipseSrcVariablePath(srcUrl, srcPathAttr);
      }
      addModuleLibrary(rootModel, element, exported, libName, url, srcUrl, macroMap);
    }
    else if (kind.equals(EclipseXml.CON_KIND)) {
      if (path.equals(EclipseXml.ECLIPSE_PLATFORM)) {
        readRequiredBundles(rootModel, refsToModules);
        addNamedLibrary(rootModel, unknownLibraries, exported, IdeaXml.ECLIPSE_LIBRARY, true);
      }
      else if (path.startsWith(EclipseXml.JRE_CONTAINER)) {

        final String jdkName = getLastPathComponent(path);
        setUpModuleJdk(rootModel, unknownJdks, eclipseModuleManager, jdkName);
      }
      else if (path.startsWith(EclipseXml.USER_LIBRARY)) {
        addNamedLibrary(rootModel, unknownLibraries, exported, getPresentableName(path), false);
      }
      else if (path.startsWith(EclipseXml.JUNIT_CONTAINER)) {
        final String junitName = IdeaXml.JUNIT + getPresentableName(path);
        addJUnitDefaultLib(rootModel, junitName, macroMap);
      } else {
        final Set<String> registeredCons = getDefinedCons();
        if (registeredCons.contains(path)) {
          eclipseModuleManager.registerCon(path);
          eclipseModuleManager.registerSrcPlace(path, idx);
        } else {
          eclipseModuleManager.registerUnknownCons(path);
          addNamedLibrary(rootModel, new ArrayList<String>(), exported, path, true);
        }
      }
    }
    else {
      throw new ConversionException("Unknown classpathentry/@kind: " + kind);
    }
  }

  protected static int srcVarStart(String srcPath) {
    return srcPath.startsWith("/") ? 1 : 0;
  }

  @NotNull
  protected static String getPresentableName(@NotNull String path) {
    return getPresentableName(path, null);
  }

  @NotNull
  protected static String getPresentableName(@NotNull String path, Set<String> names) {
    final String pathComponent = getLastPathComponent(path);
    if (pathComponent != null && names != null && !names.add(pathComponent)) return path;
    return pathComponent != null ? pathComponent : path;
  }

  @Nullable
  public static String getLastPathComponent(final String path) {
    final int idx = path.lastIndexOf('/');
    return idx < 0 || idx == path.length() - 1 ? null : path.substring(idx + 1);
  }

  protected static String getVariableRelatedPath(String var, String path) {
    return var == null ? null : ("$" + var + "$" + (path == null ? "" : ("/" + path)));
  }

  protected static String pathToUrl(String path) {
    return "file://" + path;
  }

  protected static EPathVariable createEPathVariable(final Set<String> usedVariables, final String pathAttr, final int varStart) {
    final EPathVariable var;
    int slash = pathAttr.indexOf("/", varStart);
    if (slash > 0) {
      var = new EPathVariable(usedVariables, pathAttr.substring(varStart, slash), pathAttr.substring(slash + 1));
    }
    else {
      var = new EPathVariable(usedVariables, pathAttr.substring(varStart), null);
    }
    return var;
  }

  protected String eclipseVariabledPath2Url(ExpandMacroToPathMap pathMap, Set<String> usedVariables, String path, int varStart) {
    final EPathVariable var = createEPathVariable(usedVariables, path, varStart);
    final String url = pathMap.substitute(var.toIdeaVariabledUrl(), SystemInfo.isFileSystemCaseSensitive);

    return prepareValidUrlInsideJar(url);
  }


  @Nullable
  protected String expandLinkedResourcesPath(final ExpandMacroToPathMap macroMap,
                                             final Set<String> usedVariables,
                                             final String path) {
    final EclipseProjectFinder.LinkedResource linkedResource = EclipseProjectFinder.findLinkedResource(myRootPath, path);
    if (linkedResource != null) {
      if (linkedResource.containsPathVariable()) {
        usedVariables.add(linkedResource.getVariableName());
      }
      if (linkedResource.containsPathVariable()) {
        final String toPathVariableFormat =
          getVariableRelatedPath(linkedResource.getVariableName(), linkedResource.getRelativeToVariablePath());
        return macroMap.substitute(toPathVariableFormat, SystemInfo.isFileSystemCaseSensitive);
      }
      return linkedResource.getLocation();
    }
    return null;
  }


  protected void readRequiredBundles(T rootModel, Set<String> refsToModules) throws ConversionException {
    if (myModuleNames == null) {
      return;
    }

    final File manifestFile = new File(myRootPath, "META-INF/MANIFEST.MF");
    if (!manifestFile.exists()) {
      return;
    }

    InputStream in = null;
    try {
      in = new BufferedInputStream(new FileInputStream(manifestFile));
      final Manifest manifest = new Manifest(in);
      final String attributes = manifest.getMainAttributes().getValue("Require-Bundle");
      if (!StringUtil.isEmpty(attributes)) {
        final StringTokenizer tokenizer = new StringTokenizer(attributes, ",");
        while (tokenizer.hasMoreTokens()) {
          String bundle = tokenizer.nextToken().trim();
          if (!bundle.isEmpty()) {
            final int constraintIndex = bundle.indexOf(';');
            if (constraintIndex != -1) {
              bundle = bundle.substring(0, constraintIndex).trim();
            }

            if (myModuleNames.contains(bundle)) {
              refsToModules.add(bundle);
              addInvalidModuleEntry(rootModel, false, bundle);
            }
          }
        }
      }
    }
    catch (IOException e) {
      throw new ConversionException(e.getMessage());
    }
    finally {
      if (in != null) {
        try {
          in.close();
        }
        catch (IOException ignored) {
        }
      }
    }
  }


  protected static class EPathVariable {
    private final String myVariable;
    private final String myRelatedPath;

    protected EPathVariable(final Set<String> usedVariables, final String variable, final String relatedPath) {
      myVariable = variable;
      myRelatedPath = relatedPath;
      usedVariables.add(myVariable);
    }

    public String toIdeaVariabledUrl() {
      return pathToUrl(getVariableRelatedPath(myVariable, myRelatedPath));
    }
  }
}
