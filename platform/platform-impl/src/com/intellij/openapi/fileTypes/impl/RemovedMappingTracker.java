// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.stream.Collectors;

final class RemovedMappingTracker {
  static final class RemovedMapping {
    private final FileNameMatcher myFileNameMatcher;
    private final String myFileTypeName;
    private final boolean myApproved;

    private RemovedMapping(@NotNull FileNameMatcher matcher, @NotNull String fileTypeName, boolean approved) {
      myFileNameMatcher = matcher;
      myFileTypeName = fileTypeName;
      myApproved = approved;
    }

    @NotNull
    FileNameMatcher getFileNameMatcher() {
      return myFileNameMatcher;
    }

    @NotNull
    String getFileTypeName() {
      return myFileTypeName;
    }

    boolean isApproved() {
      return myApproved;
    }

    @Override
    public String toString() {
      return "Removed mapping '" + myFileNameMatcher + "' -> " + myFileTypeName;
    }
  }

  private final Map<FileNameMatcher, RemovedMapping> myRemovedMappings = new HashMap<>();

  @NonNls private static final String ELEMENT_REMOVED_MAPPING = "removed_mapping";
  /** Applied for removed mappings approved by user */
  @NonNls private static final String ATTRIBUTE_APPROVED = "approved";
  @NonNls private static final String ATTRIBUTE_TYPE = "type";

  void clear() {
    myRemovedMappings.clear();
  }

  void add(@NotNull FileNameMatcher matcher, @NotNull String fileTypeName, boolean approved) {
    myRemovedMappings.put(matcher, new RemovedMapping(matcher, fileTypeName, approved));
  }

  void load(@NotNull Element e) {
    for (RemovedMapping mapping : readRemovedMappings(e)) {
      myRemovedMappings.put(mapping.myFileNameMatcher, mapping);
    }
  }

  @NotNull
  static List<RemovedMapping> readRemovedMappings(@NotNull Element e) {
    List<Element> children = e.getChildren(ELEMENT_REMOVED_MAPPING);
    if (children.isEmpty()) {
      return Collections.emptyList();
    }

    List<RemovedMapping> result = new ArrayList<>();
    for (Element mapping : children) {
      String ext = mapping.getAttributeValue(AbstractFileType.ATTRIBUTE_EXT);
      FileNameMatcher matcher = ext == null
                                ? FileTypeManager.parseFromString(mapping.getAttributeValue(AbstractFileType.ATTRIBUTE_PATTERN))
                                : new ExtensionFileNameMatcher(ext);
      boolean approved = Boolean.parseBoolean(mapping.getAttributeValue(ATTRIBUTE_APPROVED));
      String fileTypeName = mapping.getAttributeValue(ATTRIBUTE_TYPE);
      if (fileTypeName == null) continue;

      RemovedMapping removedMapping = new RemovedMapping(matcher, fileTypeName, approved);
      result.add(removedMapping);
    }
    return result;
  }

  void save(@NotNull Element element) {
    List<RemovedMapping> removedMappings = new ArrayList<>(myRemovedMappings.values());
    removedMappings.sort(Comparator.comparing((RemovedMapping mapping) -> mapping.getFileNameMatcher().getPresentableString()).thenComparing(RemovedMapping::getFileTypeName));
    for (RemovedMapping mapping : removedMappings) {
      Element content = writeRemovedMapping(mapping.myFileTypeName, mapping.myFileNameMatcher, true, mapping.myApproved);
      if (content != null) {
        element.addContent(content);
      }
    }
  }

  void saveRemovedMappingsForFileType(@NotNull Element map, @NotNull String fileTypeName, @NotNull Collection<? extends FileNameMatcher> associations, boolean specifyTypeName) {
    for (FileNameMatcher matcher : associations) {
      Element content = writeRemovedMapping(fileTypeName, matcher, specifyTypeName, isApproved(matcher));
      if (content != null) {
        map.addContent(content);
      }
    }
  }

  boolean hasRemovedMapping(@NotNull FileNameMatcher matcher) {
    return myRemovedMappings.containsKey(matcher);
  }

  private boolean isApproved(@NotNull FileNameMatcher matcher) {
    RemovedMapping mapping = myRemovedMappings.get(matcher);
    return mapping != null && mapping.isApproved();
   }

  @NotNull
  List<RemovedMapping> getRemovedMappings() {
    return new ArrayList<>(myRemovedMappings.values());
  }

  @NotNull
  List<FileNameMatcher> getMappingsForFileType(@NotNull String fileTypeName) {
    return myRemovedMappings.values().stream()
      .filter(mapping -> mapping.myFileTypeName.equals(fileTypeName))
      .map(mapping -> mapping.myFileNameMatcher)
      .collect(Collectors.toList());
  }

  void removeIf(@NotNull BiPredicate<? super FileNameMatcher, ? super String> predicate) {
    myRemovedMappings.entrySet().removeIf(next -> predicate.test(next.getValue().myFileNameMatcher, next.getValue().myFileTypeName));
  }

  @NotNull
  List<RemovedMapping> deleteUnapprovedMappings() {
    List<RemovedMapping> result = new ArrayList<>();
    for (Iterator<Map.Entry<FileNameMatcher, RemovedMapping>> it = myRemovedMappings.entrySet().iterator(); it.hasNext(); ) {
      Map.Entry<FileNameMatcher, RemovedMapping> next = it.next();
      if (!next.getValue().isApproved()) {
        result.add(next.getValue());
        it.remove();
      }
    }
    return result;
  }

  private static Element writeRemovedMapping(@NotNull String fileTypeName,
                                             @NotNull FileNameMatcher matcher,
                                             boolean specifyTypeName,
                                             boolean approved) {
    Element mapping = new Element(ELEMENT_REMOVED_MAPPING);
    if (!AbstractFileType.writePattern(matcher, mapping)) {
      return null;
    }
    if (approved) {
      mapping.setAttribute(ATTRIBUTE_APPROVED, "true");
    }
    if (specifyTypeName) {
      mapping.setAttribute(ATTRIBUTE_TYPE, fileTypeName);
    }

    return mapping;
  }
}
