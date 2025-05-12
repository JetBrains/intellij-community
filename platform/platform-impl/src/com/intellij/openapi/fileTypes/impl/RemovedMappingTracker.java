// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes.impl;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileTypes.ExtensionFileNameMatcher;
import com.intellij.openapi.fileTypes.FileNameMatcher;
import com.intellij.openapi.fileTypes.FileTypeManager;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.MultiMap;
import org.jdom.Element;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.VisibleForTesting;

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@ApiStatus.Internal
public final class RemovedMappingTracker {
  private static final Logger LOG = Logger.getInstance(RemovedMappingTracker.class);

  @ApiStatus.Internal
  public static final class RemovedMapping {
    private final FileNameMatcher myFileNameMatcher;
    private final String myFileTypeName;
    private final boolean myApproved;

    private RemovedMapping(@NotNull FileNameMatcher matcher, @NotNull String fileTypeName, boolean approved) {
      myFileNameMatcher = matcher;
      myFileTypeName = fileTypeName;
      myApproved = approved;
    }

    @VisibleForTesting
    public @NotNull FileNameMatcher getFileNameMatcher() {
      return myFileNameMatcher;
    }

    @VisibleForTesting
    public @NotNull String getFileTypeName() {
      return myFileTypeName;
    }

    @VisibleForTesting
    public boolean isApproved() {
      return myApproved;
    }

    @Override
    public String toString() {
      return "Removed mapping '" + myFileNameMatcher + "' -> " + myFileTypeName;
    }

    // must not look at myApproved
    @Override
    public boolean equals(Object o) {
      if (this == o) return true;
      if (o == null || getClass() != o.getClass()) return false;

      RemovedMapping mapping = (RemovedMapping)o;

      if (!myFileNameMatcher.equals(mapping.myFileNameMatcher)) return false;
      return myFileTypeName.equals(mapping.myFileTypeName);
    }

    @Override
    public int hashCode() {
      int result = myFileNameMatcher.hashCode();
      result = 31 * result + myFileTypeName.hashCode();
      return result;
    }
  }

  private final MultiMap<FileNameMatcher, RemovedMapping> myRemovedMappings = new MultiMap<>();

  private static final @NonNls String ELEMENT_REMOVED_MAPPING = "removed_mapping";
  /** Applied for removed mappings approved by user */
  private static final @NonNls String ATTRIBUTE_APPROVED = "approved";
  private static final @NonNls String ATTRIBUTE_TYPE = "type";

  @VisibleForTesting
  public void clear() {
    myRemovedMappings.clear();
  }

  @VisibleForTesting
  public @NotNull RemovedMapping add(@NotNull FileNameMatcher matcher, @NotNull String fileTypeName, boolean approved) {
    RemovedMapping mapping = new RemovedMapping(matcher, fileTypeName, approved);
    List<RemovedMapping> mappings = (List<RemovedMapping>)myRemovedMappings.getModifiable(matcher);
    boolean found = false;
    for (int i = 0; i < mappings.size(); i++) {
      RemovedMapping removedMapping = mappings.get(i);
      if (removedMapping.getFileTypeName().equals(fileTypeName)) {
        mappings.set(i, mapping);
        found = true;
        break;
      }
    }
    if (!found) {
      mappings.add(mapping);
    }
    return mapping;
  }

  void load(@NotNull Element e) {
    List<RemovedMapping> removedMappings = readRemovedMappings(e);
    Set<RemovedMapping> uniques = new LinkedHashSet<>(removedMappings.size());
    for (RemovedMapping mapping : removedMappings) {
      if (!uniques.add(mapping)) {
        LOG.warn(new InvalidDataException("Duplicate <removed_mapping> tag for " + mapping));
      }
    }
    for (RemovedMapping mapping : uniques) {
      myRemovedMappings.putValue(mapping.myFileNameMatcher, mapping);
    }
  }

  static @NotNull List<RemovedMapping> readRemovedMappings(@NotNull Element e) {
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

  void saveRemovedMappingsForFileType(@NotNull Element map,
                                      @NotNull String fileTypeName,
                                      @NotNull Collection<? extends FileNameMatcher> associations,
                                      boolean specifyTypeName) {
    for (FileNameMatcher matcher : associations) {
      Element content = writeRemovedMapping(fileTypeName, matcher, specifyTypeName, isApproved(matcher, fileTypeName));
      if (content != null) {
        map.addContent(content);
      }
    }
  }

  boolean hasRemovedMapping(@NotNull FileNameMatcher matcher) {
    return myRemovedMappings.containsKey(matcher);
  }

  private boolean isApproved(@NotNull FileNameMatcher matcher, @NotNull String fileTypeName) {
    RemovedMapping mapping = ContainerUtil.find(myRemovedMappings.get(matcher), m -> m.getFileTypeName().equals(fileTypeName));
    return mapping != null && mapping.isApproved();
   }

  @VisibleForTesting
  public @NotNull List<RemovedMapping> getRemovedMappings() {
    return new ArrayList<>(myRemovedMappings.values());
  }

  @VisibleForTesting
  public @NotNull List<FileNameMatcher> getMappingsForFileType(@NotNull String fileTypeName) {
    return myRemovedMappings.values().stream()
      .filter(mapping -> mapping.myFileTypeName.equals(fileTypeName))
      .map(mapping -> mapping.myFileNameMatcher)
      .collect(Collectors.toList());
  }

  @VisibleForTesting
  public @NotNull List<RemovedMapping> removeIf(@NotNull Predicate<? super RemovedMapping> predicate) {
    List<RemovedMapping> result = new ArrayList<>();
    for (Iterator<Map.Entry<FileNameMatcher, Collection<RemovedMapping>>> iterator = myRemovedMappings.entrySet().iterator();
         iterator.hasNext(); ) {
      Map.Entry<FileNameMatcher, Collection<RemovedMapping>> entry = iterator.next();
      Collection<RemovedMapping> mappings = entry.getValue();
      mappings.removeIf(mapping -> {
        boolean toRemove = predicate.test(mapping);
        if (toRemove) {
          result.add(mapping);
        }
        return toRemove;
      });
      if (mappings.isEmpty()) {
        iterator.remove();
      }
    }
    return result;
  }

  void approveUnapprovedMappings() {
    for (RemovedMapping mapping : new ArrayList<>(myRemovedMappings.values())) {
      if (!mapping.isApproved()) {
        myRemovedMappings.remove(mapping.getFileNameMatcher(), mapping);
        myRemovedMappings.putValue(mapping.getFileNameMatcher(), new RemovedMapping(mapping.getFileNameMatcher(), mapping.getFileTypeName(), true));
      }
    }
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
