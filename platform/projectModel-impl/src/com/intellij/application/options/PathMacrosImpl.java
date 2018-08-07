// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.application.options;

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.ModificationTracker;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@State(
  name = "PathMacrosImpl",
  storages = @Storage(value = JpsGlobalLoader.PathVariablesSerializer.STORAGE_FILE_NAME, roamingType = RoamingType.PER_OS)
)
public class PathMacrosImpl extends PathMacros implements PersistentStateComponent<Element>, ModificationTracker {
  public static final String IGNORED_MACRO_ELEMENT = "ignoredMacro";
  public static final String MAVEN_REPOSITORY = "MAVEN_REPOSITORY";

  private static final Logger LOG = Logger.getInstance(PathMacrosImpl.class);

  private static final Set<String> SYSTEM_MACROS = new THashSet<>();

  private final Map<String, String> myLegacyMacros = new THashMap<>();
  private final Map<String, String> myMacros = new LinkedHashMap<>();
  private long myModificationStamp = 0;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final List<String> myIgnoredMacros = ContainerUtil.createLockFreeCopyOnWriteList();

  static {
    SYSTEM_MACROS.add(PathMacroUtil.APPLICATION_HOME_DIR);
    SYSTEM_MACROS.add(PathMacroUtil.APPLICATION_PLUGINS_DIR);
    SYSTEM_MACROS.add(PathMacroUtil.PROJECT_DIR_MACRO_NAME);
    SYSTEM_MACROS.add(PathMacroUtil.MODULE_WORKING_DIR_NAME);
    SYSTEM_MACROS.add(PathMacroUtil.MODULE_DIR_MACRO_NAME);
    SYSTEM_MACROS.add(PathMacroUtil.USER_HOME_NAME);
  }

  public PathMacrosImpl() {
  }

  public static PathMacrosImpl getInstanceEx() {
    return (PathMacrosImpl)getInstance();
  }

  @Override
  public Set<String> getUserMacroNames() {
    myLock.readLock().lock();
    try {
      return new THashSet<>(myMacros.keySet()); // keyset should not escape the lock
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @NotNull
  public Set<String> getToolMacroNames() {
    return Collections.emptySet();
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
    try {
      myLock.writeLock().lock();
      myIgnoredMacros.clear();
      myIgnoredMacros.addAll(names);
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  @Override
  public void addIgnoredMacro(@NotNull String name) {
    if (!myIgnoredMacros.contains(name)) myIgnoredMacros.add(name);
  }

  public long getModificationCount() {
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

  @NotNull
  @Override
  public Set<String> getAllMacroNames() {
    return ContainerUtil.union(getUserMacroNames(), getSystemMacroNames());
  }

  @Nullable
  @Override
  public String getValue(@NotNull String name) {
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
      // keyset should not escape the lock
      return new THashSet<>(myLegacyMacros.keySet());
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public void setMacro(@NotNull String name, @Nullable String value) {
    try {
      myLock.writeLock().lock();

      if (StringUtil.isEmptyOrSpaces(value)) {
        if (myMacros.remove(name) != null) {
          myModificationStamp++;
        }
        return;
      }

      String prevValue = myMacros.put(name, value);
      if (!value.equals(prevValue)) {
        myModificationStamp++;
      }
    }
    finally {
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
  public void removeMacro(@NotNull String name) {
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

  @Nullable
  @Override
  public Element getState() {
    try {
      Element element = new Element("state");
      myLock.readLock().lock();

      for (Map.Entry<String, String> entry : myMacros.entrySet()) {
        String value = entry.getValue();
        if (!StringUtil.isEmptyOrSpaces(value)) {
          final Element macro = new Element(JpsGlobalLoader.PathVariablesSerializer.MACRO_TAG);
          macro.setAttribute(JpsGlobalLoader.PathVariablesSerializer.NAME_ATTRIBUTE, entry.getKey());
          macro.setAttribute(JpsGlobalLoader.PathVariablesSerializer.VALUE_ATTRIBUTE, value);
          element.addContent(macro);
        }
      }

      for (final String macro : myIgnoredMacros) {
        final Element macroElement = new Element(IGNORED_MACRO_ELEMENT);
        macroElement.setAttribute(JpsGlobalLoader.PathVariablesSerializer.NAME_ATTRIBUTE, macro);
        element.addContent(macroElement);
      }
      return element;
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public void loadState(@NotNull Element element) {
    try {
      myLock.writeLock().lock();

      for (Element macro : element.getChildren(JpsGlobalLoader.PathVariablesSerializer.MACRO_TAG)) {
        final String name = macro.getAttributeValue(JpsGlobalLoader.PathVariablesSerializer.NAME_ATTRIBUTE);
        String value = macro.getAttributeValue(JpsGlobalLoader.PathVariablesSerializer.VALUE_ATTRIBUTE);
        if (name == null || value == null) {
          continue;
        }

        if (SYSTEM_MACROS.contains(name)) {
          continue;
        }

        if (value.length() > 1 && value.charAt(value.length() - 1) == '/') {
          value = value.substring(0, value.length() - 1);
        }

        myMacros.put(name, value);
      }

      for (Element macroElement : element.getChildren(IGNORED_MACRO_ELEMENT)) {
        String ignoredName = macroElement.getAttributeValue(JpsGlobalLoader.PathVariablesSerializer.NAME_ATTRIBUTE);
        if (!StringUtil.isEmpty(ignoredName) && !myIgnoredMacros.contains(ignoredName)) {
          myIgnoredMacros.add(ignoredName);
        }
      }
    }
    finally {
      myModificationStamp++;
      myLock.writeLock().unlock();
    }
  }

  public void addMacroReplacements(@NotNull ReplacePathToMacroMap result) {
    for (String name : getUserMacroNames()) {
      String value = getValue(name);
      if (!StringUtil.isEmptyOrSpaces(value)) {
        result.addMacroReplacement(value, name);
      }
    }
  }

  public void addMacroExpands(@NotNull ExpandMacroToPathMap result) {
    for (String name : getUserMacroNames()) {
      String value = getValue(name);
      if (!StringUtil.isEmptyOrSpaces(value)) {
        result.addMacroExpand(name, value);
      }
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
