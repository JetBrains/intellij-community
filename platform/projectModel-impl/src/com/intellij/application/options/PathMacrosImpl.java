/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import com.intellij.openapi.application.PathMacros;
import com.intellij.openapi.components.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.hash.LinkedHashMap;
import gnu.trove.THashMap;
import gnu.trove.THashSet;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.model.serialization.JpsGlobalLoader;
import org.jetbrains.jps.model.serialization.PathMacroUtil;

import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

@State(
  name = "PathMacrosImpl",
  storages = @Storage(value = "path.macros.xml", roamingType = RoamingType.PER_OS)
)
public class PathMacrosImpl extends PathMacros implements PersistentStateComponent<Element> {
  private static final Logger LOG = Logger.getInstance(PathMacrosImpl.class);

  private final Map<String, String> myLegacyMacros = new THashMap<>();
  private final Map<String, String> myMacros = new LinkedHashMap<>();
  private int myModificationStamp = 0;
  private final ReentrantReadWriteLock myLock = new ReentrantReadWriteLock();
  private final List<String> myIgnoredMacros = ContainerUtil.createLockFreeCopyOnWriteList();

  private static final String MACRO_ELEMENT = JpsGlobalLoader.PathVariablesSerializer.MACRO_TAG;
  private static final String NAME_ATTR = JpsGlobalLoader.PathVariablesSerializer.NAME_ATTRIBUTE;
  private static final String VALUE_ATTR = JpsGlobalLoader.PathVariablesSerializer.VALUE_ATTRIBUTE;

  @NonNls
  public static final String IGNORED_MACRO_ELEMENT = "ignoredMacro";

  private static final Set<String> SYSTEM_MACROS = new THashSet<>();
  @NonNls public static final String EXT_FILE_NAME = "path.macros";

  static {
    SYSTEM_MACROS.add(PathMacroUtil.APPLICATION_HOME_DIR);
    SYSTEM_MACROS.add(PathMacroUtil.APPLICATION_PLUGINS_DIR);
    SYSTEM_MACROS.add(PathMacroUtil.PROJECT_DIR_MACRO_NAME);
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
    return ContainerUtil.union(getUserMacroNames(), getSystemMacroNames());
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
      // keyset should not escape the lock
      return new THashSet<>(myLegacyMacros.keySet());
    }
    finally {
      myLock.readLock().unlock();
    }
  }

  @Override
  public void setMacro(@NotNull String name, @NotNull String value) {
    if (StringUtil.isEmptyOrSpaces(value)) {
      return;
    }

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

  @Nullable
  @Override
  public Element getState() {
    try {
      Element element = new Element("state");
      myLock.writeLock().lock();

      for (Map.Entry<String, String> entry : myMacros.entrySet()) {
        String value = entry.getValue();
        if (!StringUtil.isEmptyOrSpaces(value)) {
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
      return element;
    }
    finally {
      myLock.writeLock().unlock();
    }
  }

  @Override
  public void loadState(Element element) {
    try {
      myLock.writeLock().lock();

      for (Element macro : element.getChildren(MACRO_ELEMENT)) {
        final String name = macro.getAttributeValue(NAME_ATTR);
        String value = macro.getAttributeValue(VALUE_ATTR);
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
        String ignoredName = macroElement.getAttributeValue(NAME_ATTR);
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

  public void addMacroReplacements(ReplacePathToMacroMap result) {
    for (String name : getUserMacroNames()) {
      String value = getValue(name);
      if (!StringUtil.isEmptyOrSpaces(value)) {
        result.addMacroReplacement(value, name);
      }
    }
  }

  public void addMacroExpands(ExpandMacroToPathMap result) {
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
