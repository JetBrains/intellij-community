// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.diff.impl.patch.PatchSyntaxException;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.vcs.FileStatus;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.util.xmlb.Constants;
import org.jdom.Element;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public final class ShelvedChangeList implements JDOMExternalizable, ExternalizableScheme {
  private static final Logger LOG = Logger.getInstance(ShelvedChangeList.class);

  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String ATTRIBUTE_DATE = "date";
  @NonNls private static final String ATTRIBUTE_RECYCLED_CHANGELIST = "recycled";
  @NonNls private static final String ATTRIBUTE_TOBE_DELETED_CHANGELIST = "toDelete";
  @NonNls private static final String ATTRIBUTE_DELETED_CHANGELIST = "deleted";
  @NonNls private static final String ELEMENT_BINARY = "binary";

  public Path path;
  public @NlsSafe String DESCRIPTION;
  public Date DATE;
  private volatile List<ShelvedChange> myChanges;
  private List<ShelvedBinaryFile> myBinaryFiles;
  private boolean myRecycled;
  private boolean myToDelete;
  private boolean myIsDeleted;
  private String mySchemeName;

  ShelvedChangeList() {
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
    this.path = path;
    DESCRIPTION = description;
    DATE = new Date(time);
    myBinaryFiles = binaryFiles;
    mySchemeName = DESCRIPTION;
    myChanges = shelvedChanges;
  }

  public boolean isRecycled() {
    return myRecycled;
  }

  public void setRecycled(final boolean recycled) {
    myRecycled = recycled;
  }

  @Override
  public void readExternal(@NotNull Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    path = null;
    for (Element child : element.getChildren()) {
      if (child.getName().equals(Constants.OPTION) && "PATH".equals(child.getAttributeValue(Constants.NAME))) {
        String value = child.getAttributeValue(Constants.VALUE, "");
        if (!value.isEmpty()) {
          path = Paths.get(value);
        }
      }
    }

    mySchemeName = element.getAttributeValue(NAME_ATTRIBUTE);
    DATE = new Date(Long.parseLong(element.getAttributeValue(ATTRIBUTE_DATE)));
    myRecycled = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_RECYCLED_CHANGELIST));
    myToDelete = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_TOBE_DELETED_CHANGELIST));
    myIsDeleted = Boolean.parseBoolean(element.getAttributeValue(ATTRIBUTE_DELETED_CHANGELIST));
    final List<Element> children = element.getChildren(ELEMENT_BINARY);
    myBinaryFiles = new ArrayList<>(children.size());
    for (Element child : children) {
      ShelvedBinaryFile binaryFile = new ShelvedBinaryFile();
      binaryFile.readExternal(child);
      myBinaryFiles.add(binaryFile);
    }
  }

  @Override
  public void writeExternal(@NotNull Element element) {
    writeExternal(element, this);
  }

  private static void writeExternal(@NotNull Element element, @NotNull ShelvedChangeList shelvedChangeList) {
    if (shelvedChangeList.path != null) {
      element.addContent(new Element(Constants.OPTION)
                           .setAttribute(Constants.NAME, "PATH")
                           .setAttribute(Constants.VALUE, shelvedChangeList.path.toString().replace(File.separatorChar, '/')));
    }
    DefaultJDOMExternalizer.writeExternal(shelvedChangeList, element);
    element.setAttribute(NAME_ATTRIBUTE, shelvedChangeList.getName());
    element.setAttribute(ATTRIBUTE_DATE, Long.toString(shelvedChangeList.DATE.getTime()));
    element.setAttribute(ATTRIBUTE_RECYCLED_CHANGELIST, Boolean.toString(shelvedChangeList.isRecycled()));
    if (shelvedChangeList.isMarkedToDelete()) {
      element.setAttribute(ATTRIBUTE_TOBE_DELETED_CHANGELIST, Boolean.toString(shelvedChangeList.isMarkedToDelete()));
    }
    if (shelvedChangeList.isDeleted()) {
      element.setAttribute(ATTRIBUTE_DELETED_CHANGELIST, Boolean.toString(shelvedChangeList.isDeleted()));
    }
    for (ShelvedBinaryFile file : shelvedChangeList.getBinaryFiles()) {
      Element child = new Element(ELEMENT_BINARY);
      file.writeExternal(child);
      element.addContent(child);
    }
  }

  @Nls
  @Override
  public String toString() {
    return DESCRIPTION;
  }

  public void loadChangesIfNeeded(@NotNull Project project) {
    try {
      loadChangesIfNeededOrThrow(project);
    }
    catch (Exception e) {
      LOG.error("Failed to parse the file patch: [" + path + "]", e);
    }
  }

  public void loadChangesIfNeededOrThrow(@NotNull Project project) throws VcsException {
    if (myChanges == null) {
      myChanges = loadChanges(project);
    }
  }

  private @NotNull List<ShelvedChange> loadChanges(@NotNull Project project) throws VcsException {
    try {
      List<? extends FilePatch> list = ShelveChangesManager.loadPatchesWithoutContent(project, path, null);
      return createShelvedChangesFromFilePatches(project, path, list);
    }
    catch (IOException | PatchSyntaxException e) {
      throw new VcsException(e);
    }
  }

  @Nullable
  public List<ShelvedChange> getChanges() {
    return myChanges;
  }

  @Deprecated
  public List<ShelvedChange> getChanges(Project project) {
    loadChangesIfNeeded(project);
    return getChanges();
  }

  void setChanges(List<ShelvedChange> shelvedChanges) {
    myChanges = shelvedChanges;
  }

  @NotNull
  static List<ShelvedChange> createShelvedChangesFromFilePatches(@NotNull Project project,
                                                                 @NotNull Path patchPath,
                                                                 @NotNull Collection<? extends FilePatch> filePatches) {
    List<ShelvedChange> changes = new ArrayList<>();
    for (FilePatch patch : filePatches) {
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
      changes.add(new ShelvedChange(project, patchPath, patch.getBeforeName(), patch.getAfterName(), status));
    }
    return changes;
  }

  public List<ShelvedBinaryFile> getBinaryFiles() {
    return myBinaryFiles;
  }

  @NotNull
  @Override
  public String getName() {
    return mySchemeName;
  }

  @Override
  public void setName(@NotNull String newName) {
    mySchemeName = newName;
  }

  public boolean isValid() {
    return Files.exists(path);
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

  /**
   * Update Date while recycle or restore shelvedChangelist
   */
  public void updateDate() {
    DATE = new Date(System.currentTimeMillis());
  }
}
