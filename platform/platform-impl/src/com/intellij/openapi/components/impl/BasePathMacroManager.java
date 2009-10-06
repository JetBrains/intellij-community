package com.intellij.openapi.components.impl;

import com.intellij.application.options.PathMacrosImpl;
import com.intellij.application.options.ReplacePathToMacroMap;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.components.ExpandMacroToPathMap;
import com.intellij.openapi.components.PathMacroManager;
import com.intellij.openapi.components.TrackingPathMacroSubstitutor;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.util.containers.FactoryMap;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;

import java.util.*;

public class BasePathMacroManager extends PathMacroManager {
  private PathMacrosImpl myPathMacros;
  private boolean myUseUserMacroses;


  public BasePathMacroManager(boolean useUserMacroses) {
    myUseUserMacroses = useUserMacroses;
  }

  public BasePathMacroManager() {
    this(true);
  }

  public ExpandMacroToPathMap getExpandMacroMap() {
    ExpandMacroToPathMap result = new ExpandMacroToPathMap();
    result.addMacroExpand(PathMacrosImpl.APPLICATION_HOME_MACRO_NAME, PathManager.getHomePath());
    if (myUseUserMacroses) {
      getPathMacros().addMacroExpands(result);
    }
    return result;
  }


  public ReplacePathToMacroMap getReplacePathMap() {
    ReplacePathToMacroMap result = new ReplacePathToMacroMap();

    result.addMacroReplacement(PathManager.getHomePath(), PathMacrosImpl.APPLICATION_HOME_MACRO_NAME);
    if (myUseUserMacroses) {
      getPathMacros().addMacroReplacements(result);
    }

    return result;
  }

  public TrackingPathMacroSubstitutor createTrackingSubstitutor() {
    return new MyTrackingPathMacroSubstitutor();
  }

  public String expandPath(final String path) {
    return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public String collapsePath(final String path) {
    return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
  }

  public void collapsePathsRecursively(final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive, true);
  }

  public void expandPaths(final Element element) {
    getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }


  public void collapsePaths(final Element element) {
    getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
  }

  public PathMacrosImpl getPathMacros() {
    if (myPathMacros == null) {
      myPathMacros = PathMacrosImpl.getInstanceEx();
    }

    return myPathMacros;
  }


  protected static void putIfAbsent(final ReplacePathToMacroMap result, @NonNls final String pathWithPrefix, @NonNls final String substWithPrefix, final boolean check) {
    if (check && result.get(pathWithPrefix) != null) return;
    result.put(pathWithPrefix, substWithPrefix);
  }

  private class MyTrackingPathMacroSubstitutor implements TrackingPathMacroSubstitutor {
    private final Map<String, Set<String>> myMacroToComponentNames = new FactoryMap<String, Set<String>>() {
      @Override
      protected Set<String> create(String key) {
        return new HashSet<String>();
      }
    };

    private final Map<String, Set<String>> myComponentNameToMacros = new FactoryMap<String, Set<String>>() {
      @Override
      protected Set<String> create(String key) {
        return new HashSet<String>();
      }
    };

    public MyTrackingPathMacroSubstitutor() {
    }

    public void reset() {
      myMacroToComponentNames.clear();
      myComponentNameToMacros.clear();
    }

    public String expandPath(final String path) {
      return getExpandMacroMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
    }

    public String collapsePath(final String path) {
      return getReplacePathMap().substitute(path, SystemInfo.isFileSystemCaseSensitive);
    }

    public void expandPaths(final Element element) {
      getExpandMacroMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
    }

    public void collapsePaths(final Element element) {
      getReplacePathMap().substitute(element, SystemInfo.isFileSystemCaseSensitive);
    }

    public int hashCode() {
      return getExpandMacroMap().hashCode();
    }

    public void invalidateUnknownMacros(final Set<String> macros) {
      for (final String macro : macros) {
        final Set<String> components = myMacroToComponentNames.get(macro);
        for (final String component : components) {
          myComponentNameToMacros.remove(component);
        }

        myMacroToComponentNames.remove(macro);
      }
    }

    public Collection<String> getComponents(final Collection<String> macros) {
      final Set<String> result = new HashSet<String>();
      for (String macro : myMacroToComponentNames.keySet()) {
        if (macros.contains(macro)) {
          result.addAll(myMacroToComponentNames.get(macro));
        }
      }

      return result;
    }

    public Collection<String> getUnknownMacros(final String componentName) {
      if (componentName == null) {
        return Collections.unmodifiableSet(myMacroToComponentNames.keySet());
      } else {
        return Collections.unmodifiableSet(myComponentNameToMacros.get(componentName));
      }
    }

    public void addUnknownMacros(final String componentName, final Collection<String> unknownMacros) {
      if (unknownMacros.isEmpty()) return;
      
      for (String unknownMacro : unknownMacros) {
        final Set<String> stringList = myMacroToComponentNames.get(unknownMacro);
        stringList.add(componentName);
      }

      myComponentNameToMacros.get(componentName).addAll(unknownMacros);
    }
  }
}
