// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.components.PathMacroSubstitutor;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.util.ObjectUtils;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xmlb.Constants;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

public final class ShelvedChangeList implements ExternalizableScheme {
  private static final Logger LOG = Logger.getInstance(ShelvedChangeList.class);

  private static final @NonNls String NAME_ATTRIBUTE = "name";
  private static final @NonNls String ATTRIBUTE_DATE = "date";
  private static final @NonNls String ATTRIBUTE_RECYCLED_CHANGELIST = "recycled";
  private static final @NonNls String ATTRIBUTE_TOBE_DELETED_CHANGELIST = "toDelete";
  private static final @NonNls String ATTRIBUTE_DELETED_CHANGELIST = "deleted";
  private static final @NonNls String ELEMENT_BINARY = "binary";
  private static final @NonNls String PATH_FIELD_NAME = "PATH";
  private static final @NonNls String DESCRIPTION_FIELD_NAME = "DESCRIPTION";

  private Path myPath;
  private @NotNull String mySchemeName;
  /**
   * Use {@link ShelvedChangeList#getDescription()} and {@link ShelvedChangeList#setDescription(String)} instead.
   */
  @ApiStatus.Internal public @NlsSafe String DESCRIPTION;
  private Date myDate;

  private boolean myRecycled;
  private boolean myToDelete;
  private boolean myIsDeleted;

  private final List<ShelvedBinaryFile> myBinaryFiles;

  private volatile List<ShelvedChange> myChanges;
  private volatile @Nls String myChangesLoadingError = null;

  ShelvedChangeList(@Nullable Path path, @NotNull String name, @NlsSafe String description,
                    long time, boolean isRecycled, boolean isToDelete, boolean isDeleted,
                    @NotNull List<ShelvedBinaryFile> binaryFiles) {
    myPath = path;
    mySchemeName = name;
    DESCRIPTION = description;
    myDate = new Date(time);
    myRecycled = isRecycled;
    myToDelete = isToDelete;
    myIsDeleted = isDeleted;
    myBinaryFiles = binaryFiles;
  }

  public ShelvedChangeList(@NotNull Path path,
                           @NlsSafe String description,
                           List<ShelvedBinaryFile> binaryFiles,
                           @NotNull List<ShelvedChange> shelvedChanges) {
    this(path, description, binaryFiles, shelvedChanges, System.currentTimeMillis());
  }

  ShelvedChangeList(@NotNull Path path,
                    @NlsSafe String description,
                    List<ShelvedBinaryFile> binaryFiles,
                    @NotNull List<ShelvedChange> shelvedChanges,
                    long time) {
    this(path, description, description, time, false, false, false, binaryFiles);
    myChanges = shelvedChanges;
  }

  public boolean isRecycled() {
    return myRecycled;
  }

  public void setRecycled(final boolean recycled) {
    myRecycled = recycled;
  }

  @Override
  public @Nls String toString() {
    return DESCRIPTION;
  }

  public void loadChangesIfNeeded(@NotNull Project project) {
    if (myChanges != null) return;
    try {
      myChangesLoadingError = null;

      List<? extends FilePatch> list = ShelveChangesManager.loadPatchesWithoutContent(project, myPath, null);

      List<ShelvedChange> changes = new ArrayList<>();
      for (FilePatch patch : list) {
        ShelvedChange change = createShelvedChange(project, myPath, patch);
        if (change != null) {
          changes.add(change);
        }
        else if (myChangesLoadingError == null) {
          String patchName = ObjectUtils.coalesce(patch.getBeforeName(), patch.getAfterName(), myPath.toString());
          myChangesLoadingError = VcsBundle.message("shelve.loading.patch.error", patchName);
        }
      }
      myChanges = changes;
    }
    catch (ProcessCanceledException pce) {
      throw pce;
    }
    catch (Throwable e) {
      LOG.warn("Failed to parse the file patch: [" + myPath + "]", e);
      myChanges = Collections.emptyList();
      myChangesLoadingError = VcsBundle.message("shelve.loading.patch.error", e.getMessage());
    }
  }

  public @Nullable List<ShelvedChange> getChanges() {
    return myChanges;
  }

  @Deprecated(forRemoval = true)
  public List<ShelvedChange> getChanges(Project project) {
    loadChangesIfNeeded(project);
    return getChanges();
  }

  public @Nullable @Nls String getChangesLoadingError() {
    return myChangesLoadingError;
  }

  void setChanges(List<ShelvedChange> shelvedChanges) {
    myChanges = shelvedChanges;
  }

  static @NotNull List<ShelvedChange> createShelvedChangesFromFilePatches(@NotNull Project project,
                                                                          @NotNull Path patchPath,
                                                                          @NotNull Collection<? extends FilePatch> filePatches) {
    return ContainerUtil.mapNotNull(filePatches, patch -> createShelvedChange(project, patchPath, patch));
  }

  static @Nullable ShelvedChange createShelvedChange(@NotNull Project project, @NotNull Path patchPath, @NotNull FilePatch patch) {
    String beforeName = patch.getBeforeName();
    String afterName = patch.getAfterName();
    if (beforeName == null || afterName == null) {
      LOG.warn("Failed to parse the file patch: [" + patchPath + "]:" + patch);
      return null;
    }

    FileStatus status;
    if (patch.isNewFile()) {
      status = FileStatus.ADDED;
    }
    else if (patch.isDeletedFile()) {
      status = FileStatus.DELETED;
    }
    else {
      status = FileStatus.MODIFIED;
    }

    return ShelvedChange.create(project, patchPath, beforeName, afterName, status);
  }

  public List<ShelvedBinaryFile> getBinaryFiles() {
    return myBinaryFiles;
  }

  @Override
  public @NotNull String getName() {
    return mySchemeName;
  }

  @Override
  public void setName(@NotNull String newName) {
    mySchemeName = newName;
  }

  public boolean isValid() {
    return Files.exists(myPath);
  }

  public void markToDelete(boolean toDeleted) {
    myToDelete = toDeleted;
  }

  public boolean isMarkedToDelete() {
    return myToDelete;
  }

  public void setDeleted(boolean isDeleted) {
    myIsDeleted = isDeleted;
  }

  public boolean isDeleted() {
    return myIsDeleted;
  }

  public @Nullable Path getPath() {
    return myPath;
  }

  public @NlsSafe @NotNull String getDescription() {
    return Objects.requireNonNullElse(DESCRIPTION, "");
  }

  @ApiStatus.Internal
  public void setDescription(@NotNull @NlsSafe String description) {
    DESCRIPTION = description;
  }

  public @NotNull Date getDate() {
    return Objects.requireNonNullElseGet(myDate, () -> new Date(System.currentTimeMillis()));
  }

  public void setDate(@NotNull Date date) {
    myDate = date;
  }

  /**
   * Update Date while recycle or restore shelvedChangelist
   */
  public void updateDate() {
    myDate = new Date(System.currentTimeMillis());
  }

  public static @NotNull ShelvedChangeList readExternal(@NotNull Element element,
                                                        @NotNull PathMacroSubstitutor pathMacroSubstitutor) throws InvalidDataException {
    Path path = null;
    String description = null;
    for (Map.Entry<String, String> field : readFields(element).entrySet()) {
      if (PATH_FIELD_NAME.equals(field.getKey())) {
        String value = pathMacroSubstitutor.expandPath(field.getValue());
        if (!value.isEmpty()) {
          path = Paths.get(value);
        }
      }
      else if (DESCRIPTION_FIELD_NAME.equals(field.getKey())) {
        description = field.getValue();
      }
    }

    String name = element.getAttributeValue(NAME_ATTRIBUTE, "");
    long time = Long.parseLong(element.getAttributeValue(ATTRIBUTE_DATE));
    boolean isRecycled = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_RECYCLED_CHANGELIST));
    boolean isToDelete = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_TOBE_DELETED_CHANGELIST));
    boolean isDeleted = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_DELETED_CHANGELIST));

    List<Element> children = element.getChildren(ELEMENT_BINARY);
    List<ShelvedBinaryFile> binaryFiles = new ArrayList<>(children.size());
    for (Element child : children) {
      ShelvedBinaryFile binaryFile = ShelvedBinaryFile.readExternal(child, pathMacroSubstitutor);
      binaryFiles.add(binaryFile);
    }

    return new ShelvedChangeList(path, name, description, time, isRecycled, isToDelete, isDeleted, binaryFiles);
  }

  public static void writeExternal(@NotNull ShelvedChangeList shelvedChangeList, @NotNull Element element,
                                   @Nullable PathMacroSubstitutor pathMacroSubstitutor) {
    if (shelvedChangeList.myPath != null) {
      String pathString = collapsePath(shelvedChangeList.myPath.toString().replace(File.separatorChar, '/'), pathMacroSubstitutor);
      writeField(element, PATH_FIELD_NAME, pathString);
    }
    writeField(element, DESCRIPTION_FIELD_NAME, shelvedChangeList.DESCRIPTION);
    element.setAttribute(NAME_ATTRIBUTE, shelvedChangeList.getName());
    element.setAttribute(ATTRIBUTE_DATE, Long.toString(shelvedChangeList.myDate.getTime()));
    element.setAttribute(ATTRIBUTE_RECYCLED_CHANGELIST, Boolean.toString(shelvedChangeList.isRecycled()));
    if (shelvedChangeList.isMarkedToDelete()) {
      element.setAttribute(ATTRIBUTE_TOBE_DELETED_CHANGELIST, "true");
    }
    if (shelvedChangeList.isDeleted()) {
      element.setAttribute(ATTRIBUTE_DELETED_CHANGELIST, "true");
    }
    for (ShelvedBinaryFile file : shelvedChangeList.getBinaryFiles()) {
      Element child = new Element(ELEMENT_BINARY);
      file.writeExternal(child, pathMacroSubstitutor);
      element.addContent(child);
    }
  }

  static void writeField(@NotNull Element element, @NotNull String name, @Nullable String value) {
    if (value == null) return;
    element.addContent(new Element(Constants.OPTION)
                         .setAttribute(Constants.NAME, name)
                         .setAttribute(Constants.VALUE, value));
  }

  static @Nullable String collapsePath(@Nullable String path, @Nullable PathMacroSubstitutor pathMacroSubstitutor) {
    if (pathMacroSubstitutor == null || path == null) return path;
    return pathMacroSubstitutor.collapsePath(path);
  }

  static @NotNull Map<String, String> readFields(@NotNull Element element) {
    Map<String, String> result = new HashMap<>();
    for (Element child : element.getChildren()) {
      if (child.getName().equals(Constants.OPTION)) {
        String fieldName = child.getAttributeValue(Constants.NAME);
        String fieldValue = child.getAttributeValue(Constants.VALUE);
        if (fieldName != null && fieldValue != null) {
          result.put(fieldName, fieldValue);
        }
      }
    }
    return result;
  }
}
