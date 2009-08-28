/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.util.ArrayUtil;
import gnu.trove.THashMap;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author max
 */
public class FileTypeAssocTable<T> {
  private final Map<String, T> myExtensionMappings;
  private final List<Pair<FileNameMatcher, T>> myMatchingMappings;

  private FileTypeAssocTable(final Map<String, T> extensionMappings, final List<Pair<FileNameMatcher, T>> matchingMappings) {
    myExtensionMappings = new THashMap<String, T>(extensionMappings);
    myMatchingMappings = new ArrayList<Pair<FileNameMatcher, T>>(matchingMappings);
  }

  public FileTypeAssocTable() {
    this(Collections.<String, T>emptyMap(), Collections.<Pair<FileNameMatcher, T>>emptyList());
  }

  public boolean isAssociatedWith(T type, FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      return myExtensionMappings.get(((ExtensionFileNameMatcher)matcher).getExtension()) == type;
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst()) && type == mapping.getSecond()) return true;
    }

    return false;
  }

  public void addAssociation(FileNameMatcher matcher, T type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      myExtensionMappings.put(((ExtensionFileNameMatcher)matcher).getExtension(), type);
    }
    else {
      myMatchingMappings.add(new Pair<FileNameMatcher, T>(matcher, type));
    }
  }

  public boolean removeAssociation(FileNameMatcher matcher, T type) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      String extension = ((ExtensionFileNameMatcher)matcher).getExtension();
      if (myExtensionMappings.get(extension) == type) {
        myExtensionMappings.remove(extension);
        return true;
      }
      return false;
    }

    List<Pair<FileNameMatcher, T>> copy = new ArrayList<Pair<FileNameMatcher, T>>(myMatchingMappings);
    for (Pair<FileNameMatcher, T> assoc : copy) {
      if (matcher.equals(assoc.getFirst())) {
        myMatchingMappings.remove(assoc);
        return true;
      }
    }

    return false;
  }

  public boolean removeAllAssociations(T type) {
    boolean changed = false;
    Set<String> exts = myExtensionMappings.keySet();
    String[] extsStrings = ArrayUtil.toStringArray(exts);
    for (String s : extsStrings) {
      if (myExtensionMappings.get(s) == type) {
        myExtensionMappings.remove(s);
        changed = true;
      }
    }

    List<Pair<FileNameMatcher, T>> copy = new ArrayList<Pair<FileNameMatcher, T>>(myMatchingMappings);
    for (Pair<FileNameMatcher, T> assoc : copy) {
      if (assoc.getSecond() == type) {
        myMatchingMappings.remove(assoc);
        changed = true;
      }
    }

    return changed;
  }

  @Nullable
  public T findAssociatedFileType(@NotNull @NonNls String fileName) {
    //noinspection ForLoopReplaceableByForEach
    for (int i = 0; i < myMatchingMappings.size(); i++) {
      final Pair<FileNameMatcher, T> mapping = myMatchingMappings.get(i);
      if (mapping.getFirst().accept(fileName)) return mapping.getSecond();
    }

    return myExtensionMappings.get(FileUtil.getExtension(fileName));
  }

  @Nullable
  public T findAssociatedFileType(final FileNameMatcher matcher) {
    if (matcher instanceof ExtensionFileNameMatcher) {
      return myExtensionMappings.get(((ExtensionFileNameMatcher)matcher).getExtension());
    }

    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (matcher.equals(mapping.getFirst())) return mapping.getSecond();
    }

    return null;
  }

  @Deprecated
  @NotNull
  public String[] getAssociatedExtensions(T type) {
    Map<String, T> extMap = myExtensionMappings;

    List<String> exts = new ArrayList<String>();
    for (String ext : extMap.keySet()) {
      if (extMap.get(ext) == type) {
        exts.add(ext);
      }
    }
    return ArrayUtil.toStringArray(exts);
  }

  @NotNull
  public FileTypeAssocTable<T> copy() {
    return new FileTypeAssocTable<T>(myExtensionMappings, myMatchingMappings);
  }

  @NotNull
  public List<FileNameMatcher> getAssociations(final T type) {
    List<FileNameMatcher> result = new ArrayList<FileNameMatcher>();
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getSecond() == type) {
        result.add(mapping.getFirst());
      }
    }

    for (Map.Entry<String, T> entries : myExtensionMappings.entrySet()) {
      if (entries.getValue() == type) {
        result.add(new ExtensionFileNameMatcher(entries.getKey()));
      }
    }

    return result;
  }

  public boolean hasAssociationsFor(final T fileType) {
    if (myExtensionMappings.values().contains(fileType)) return true;
    for (Pair<FileNameMatcher, T> mapping : myMatchingMappings) {
      if (mapping.getSecond() == fileType) return true;
    }
    return false;
  }

  public boolean equals(final Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;

    final FileTypeAssocTable that = (FileTypeAssocTable)o;

    if (!myExtensionMappings.equals(that.myExtensionMappings)) return false;
    if (!myMatchingMappings.equals(that.myMatchingMappings)) return false;

    return true;
  }

  public int hashCode() {
    int result;
    result = myExtensionMappings.hashCode();
    result = 31 * result + myMatchingMappings.hashCode();
    return result;
  }
}
