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
package com.intellij.application.options;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NamedJDOMExternalizable;
import com.intellij.openapi.util.RoamingTypeDisabled;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.HashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * @author dsl
 */
public class PathMacrosImpl extends PathMacros implements ApplicationComponent, NamedJDOMExternalizable, RoamingTypeDisabled {
  private static final Logger LOG = Logger.getInstance("#com.intellij.application.options.PathMacrosImpl");
  private final Map<String, String> myLegacyMacros = new HashMap<String, String>();
  private final Map<String, String> myMacros = new HashMap<String, String>();
  private int myModificationStamp = 0;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final List<String> myIgnoredMacros = ContainerUtil.createLockFreeCopyOnWriteList();

  public static final String MACRO_ELEMENT = JpsGlobalLoader.PathVariablesSerializer.MACRO_TAG;
  public static final String NAME_ATTR = JpsGlobalLoader.PathVariablesSerializer.NAME_ATTRIBUTE;
  public static final String VALUE_ATTR = JpsGlobalLoader.PathVariablesSerializer.VALUE_ATTRIBUTE;

  @NonNls
  public static final String IGNORED_MACRO_ELEMENT = "ignoredMacro";

  // predefined macros
  @NonNls
  public static final String APPLICATION_HOME_MACRO_NAME = PathMacroUtil.APPLICATION_HOME_DIR;
  @NonNls
  public static final String PROJECT_DIR_MACRO_NAME = PathMacroUtil.PROJECT_DIR_MACRO_NAME;
  @NonNls
  public static final String MODULE_DIR_MACRO_NAME = PathMacroUtil.MODULE_DIR_MACRO_NAME;
  @NonNls
  public static final String USER_HOME_MACRO_NAME = PathMacroUtil.USER_HOME_NAME;

  private static final Set<String> SYSTEM_MACROS = new HashSet<String>();
  @NonNls public static final String EXT_FILE_NAME = "path.macros";

  static {
    SYSTEM_MACROS.add(APPLICATION_HOME_MACRO_NAME);
    SYSTEM_MACROS.add(PathMacroUtil.APPLICATION_PLUGINS_DIR);
    SYSTEM_MACROS.add(PROJECT_DIR_MACRO_NAME);
    SYSTEM_MACROS.add(MODULE_DIR_MACRO_NAME);
    SYSTEM_MACROS.add(USER_HOME_MACRO_NAME);
  }

  private static final Set<String> ourToolsMacros = ContainerUtil.immutableSet(
    "ClasspathEntry",
    "Classpath",
    "ColumnNumber",
    "FileClass",
    "FileDir",
    "FileParentDir",
    "FileDirName",
    "FileDirPathFromParent",
    "FileDirRelativeToProjectRoot",
    "/FileDirRelativeToProjectRoot",
    "FileDirRelativeToSourcepath",
    "/FileDirRelativeToSourcepath",
    "FileExt",
    "FileFQPackage",
    "FileName",
    "FileNameWithoutExtension",
    "FileNameWithoutAllExtensions",
    "FilePackage",
    "FilePath",
    "UnixSeparators",
    "FilePathRelativeToProjectRoot",
    "/FilePathRelativeToProjectRoot",
    "FilePathRelativeToSourcepath",
    "/FilePathRelativeToSourcepath",
    "FilePrompt",
    "FileRelativeDir",
    "/FileRelativeDir",
    "FileRelativePath",
    "/FileRelativePath",
    "FileEncoding",
    "JavaDocPath",
    "JDKPath",
    "LineNumber",
    "ModuleFileDir",
    "ModuleFilePath",
    "ModuleName",
    "ModuleSourcePath",
    "ModuleSdkPath",
    "OutputPath",
    "PhpExecutable",
    "ProjectFileDir",
    "ProjectFilePath",
    "ProjectName",
    "Projectpath",
    "Prompt",
    "SourcepathEntry",
    "Sourcepath",
    "SHOW_CHANGES",
    "ClipboardContent",
    "SelectedText",
    "SelectionStartLine",
    "SelectionEndLine",
    "SelectionStartColumn",
    "SelectionEndColumn",
    "PyInterpreterDirectory"
  );

  public PathMacrosImpl() {
    //setMacro(USER_HOME_MACRO_NAME, FileUtil.toSystemIndependentName(SystemProperties.getUserHome()));
  }

  public static PathMacrosImpl getInstanceEx() {
    return (PathMacrosImpl)ApplicationManager.getApplication().getComponent(PathMacros.class);
  }

  @Override
  @NotNull
  public String getComponentName() {
    return "PathMacrosImpl";
  }

  @Override
  public void initComponent() {
  }

  @Override
  public void disposeComponent() {
  }

  @Override
  public String getExternalFileName() {
    return EXT_FILE_NAME;
  }

  @Override
  public Set<String> getUserMacroNames() {
    myLock.readLock().lock();
    try {
      return new THashSet<String>(myMacros.keySet()); // keyset should not escape the lock
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  public static Set<String> getToolMacroNames() {
    return ourToolsMacros;
  }

  @Override
  public Set<String> getSystemMacroNames() {
    return SYSTEM_MACROS;
  }

  @Override
  public Collection<String> getIgnoredMacroNames() {
    return myIgnoredMacros;
  }

  @Override
  public void setIgnoredMacroNames(@NotNull final Collection<String> names) {
    myIgnoredMacros.clear();
    myIgnoredMacros.addAll(names);
  }

  @Override
  public void addIgnoredMacro(@NotNull String name) {
    if (!myIgnoredMacros.contains(name)) myIgnoredMacros.add(name);
  }

  public int getModificationStamp() {
    myLock.readLock().lock();
    try {
      return myModificationStamp;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public boolean isIgnoredMacroName(@NotNull String macro) {
    return myIgnoredMacros.contains(macro);
  }

  @Override
  public Set<String> getAllMacroNames() {
    final Set<String> userMacroNames = getUserMacroNames();
    final Set<String> systemMacroNames = getSystemMacroNames();
    final Set<String> allNames = new HashSet<String>(userMacroNames.size() + systemMacroNames.size());
    allNames.addAll(systemMacroNames);
    allNames.addAll(userMacroNames);
    return allNames;
  }

  @Override
  public String getValue(String name) {
    try {
      myLock.readLock().lock();
      return myMacros.get(name);
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public void removeAllMacros() {
    try {
      myLock.writeLock().lock();
      myMacros.clear();
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  @Override
  public Collection<String> getLegacyMacroNames() {
    try {
      myLock.readLock().lock();
      return new THashSet<String>(myLegacyMacros.keySet()); // keyset should not escape the lock
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public void setMacro(@NotNull String name, @NotNull String value) {
    if (value.trim().isEmpty()) return;
    try {
      myLock.writeLock().lock();
      myMacros.put(name, value);
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  @Override
  public void addLegacyMacro(@NotNull String name, @NotNull String value) {
    try {
      myLock.writeLock().lock();
      myLegacyMacros.put(name, value);
      myMacros.remove(name);
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  @Override
  public void removeMacro(String name) {
    try {
      myLock.writeLock().lock();
      final String value = myMacros.remove(name);
      LOG.assertTrue(value != null);
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  @Override
  public void readExternal(Element element) throws InvalidDataException {
    try {
      myLock.writeLock().lock();

      final List children = element.getChildren(MACRO_ELEMENT);
      for (Object aChildren : children) {
        Element macro = (Element)aChildren;
        final String name = macro.getAttributeValue(NAME_ATTR);
        String value = macro.getAttributeValue(VALUE_ATTR);
        if (name == null || value == null) {
          throw new InvalidDataException();
        }

        if (SYSTEM_MACROS.contains(name)) {
          continue;
        }

        if (value.length() > 1 && value.charAt(value.length() - 1) == '/') {
          value = value.substring(0, value.length() - 1);
        }

        myMacros.put(name, value);
      }

      final List ignoredChildren = element.getChildren(IGNORED_MACRO_ELEMENT);
      for (final Object child : ignoredChildren) {
        final Element macroElement = (Element)child;
        final String ignoredName = macroElement.getAttributeValue(NAME_ATTR);
        if (ignoredName != null && !ignoredName.isEmpty() && !myIgnoredMacros.contains(ignoredName)) {
          myIgnoredMacros.add(ignoredName);
        }
      }
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  @Override
  public void writeExternal(Element element) throws WriteExternalException {
    try {
      myLock.writeLock().lock();

      final Set<Map.Entry<String, String>> entries = myMacros.entrySet();
      for (Map.Entry<String, String> entry : entries) {
        final String value = entry.getValue();
        if (value != null && !value.trim().isEmpty()) {
          final Element macro = new Element(MACRO_ELEMENT);
          macro.setAttribute(NAME_ATTR, entry.getKey());
          macro.setAttribute(VALUE_ATTR, value);
          element.addContent(macro);
        }
      }

      for (final String macro : myIgnoredMacros) {
        final Element macroElement = new Element(IGNORED_MACRO_ELEMENT);
        macroElement.setAttribute(NAME_ATTR, macro);
        element.addContent(macroElement);
      }
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  public void addMacroReplacements(ReplacePathToMacroMap result) {
    for (final String name : getUserMacroNames()) {
      final String value = getValue(name);
      if (value != null && !value.trim().isEmpty()) result.addMacroReplacement(value, name);
    }
  }


  public void addMacroExpands(ExpandMacroToPathMap result) {
    for (final String name : getUserMacroNames()) {
      final String value = getValue(name);
      if (value != null && !value.trim().isEmpty()) result.addMacroExpand(name, value);
    }

    myLock.readLock().lock();
    try {
      for (Map.Entry<String, String> entry : myLegacyMacros.entrySet()) {
        result.addMacroExpand(entry.getKey(), entry.getValue());
      }
    }
    finally {
      myLock.readLock().unlock();
    }
  }
}
