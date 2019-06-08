/*
 * Copyright 2000-2015 JetBrains s.r.o.
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

package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.diff.impl.patch.FilePatch;
import com.intellij.openapi.options.ExternalizableScheme;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.JDOMExternalizable;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.io.FileUtil;
import com.intellij.openapi.vcs.FileStatus;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class ShelvedChangeList implements JDOMExternalizable, ExternalizableScheme {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelvedChangeList");

  @NonNls private static final String NAME_ATTRIBUTE = "name";
  @NonNls private static final String ATTRIBUTE_DATE = "date";
  @NonNls private static final String ATTRIBUTE_RECYCLED_CHANGELIST = "recycled";
  @NonNls private static final String ATTRIBUTE_TOBE_DELETED_CHANGELIST = "toDelete";
  @NonNls private static final String ATTRIBUTE_DELETED_CHANGELIST = "deleted";
  @NonNls private static final String ELEMENT_BINARY = "binary";

  public String PATH;
  public String DESCRIPTION;
  public Date DATE;
  private volatile List<ShelvedChange> myChanges;
  private List<ShelvedBinaryFile> myBinaryFiles;
  private boolean myRecycled;
  private boolean myToDelete;
  private boolean myIsDeleted;
  private String mySchemeName;

  ShelvedChangeList() {
  }

  public ShelvedChangeList(final String path,
                           final String description,
                           final List<ShelvedBinaryFile> binaryFiles,
                           @NotNull List<ShelvedChange> shelvedChanges) {
    this(path, description, binaryFiles, shelvedChanges, System.currentTimeMillis());
  }

  ShelvedChangeList(final String path,
                    final String description,
                    final List<ShelvedBinaryFile> binaryFiles,
                    @NotNull List<ShelvedChange> shelvedChanges,
                    final long time) {
    PATH = FileUtil.toSystemIndependentName(path);
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
  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    PATH = FileUtil.toSystemIndependentName(PATH);
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
  public void writeExternal(@NotNull Element element) throws WriteExternalException {
    writeExternal(element, this);
  }

  private static void writeExternal(@NotNull Element element, @NotNull ShelvedChangeList shelvedChangeList) throws WriteExternalException {
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

  @Override
  public String toString() {
    return DESCRIPTION;
  }

  public void loadChangesIfNeeded(@NotNull Project project) {
    if (myChanges == null) {
      try {
        final List<? extends FilePatch> list = ShelveChangesManager.loadPatchesWithoutContent(project, PATH, null);
        myChanges = createShelvedChangesFromFilePatches(project, PATH, list);
      }
      catch (Exception e) {
        LOG.error("Failed to parse the file patch: [" + PATH + "]", e);
      }
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
                                                                 @NotNull String patchPath,
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
    return new File(PATH).exists();
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
