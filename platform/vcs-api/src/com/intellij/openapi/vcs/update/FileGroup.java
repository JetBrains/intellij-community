/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.util.*;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class FileGroup implements JDOMExternalizable {

  public String myUpdateName;
  public String myStatusName;
  private final Map<String, String> myErrorsMap = new HashMap<>();

  private final Collection<UpdatedFile> myFiles = new ArrayList<>();
  public boolean mySupportsDeletion;
  public boolean myCanBeAbsent;
  public String myId;
  @NonNls private static final String PATH = "PATH";
  @NonNls private static final String VCS_ATTRIBUTE = "vcs";
  @NonNls private static final String REVISION_ATTRIBUTE = "revision";

  private final List<FileGroup> myChildren = new ArrayList<>();
  @NonNls private static final String FILE_GROUP_ELEMENT_NAME = "FILE-GROUP";

  @NonNls public static final String MODIFIED_ID = "MODIFIED";
  @NonNls public static final String MERGED_WITH_CONFLICT_ID = "MERGED_WITH_CONFLICTS";
  @NonNls public static final String MERGED_WITH_TREE_CONFLICT = "MERGED_WITH_TREE_CONFLICT";
  @NonNls public static final String MERGED_WITH_PROPERTY_CONFLICT_ID = "MERGED_WITH_PROPERTY_CONFLICT";
  @NonNls public static final String MERGED_ID = "MERGED";
  @NonNls public static final String UNKNOWN_ID = "UNKNOWN";
  @NonNls public static final String LOCALLY_ADDED_ID = "LOCALLY_ADDED";
  @NonNls public static final String LOCALLY_REMOVED_ID = "LOCALLY_REMOVED";
  @NonNls public static final String UPDATED_ID = "UPDATED";
  @NonNls public static final String REMOVED_FROM_REPOSITORY_ID = "REMOVED_FROM_REPOSITORY";
  @NonNls public static final String CREATED_ID = "CREATED";
  @NonNls public static final String RESTORED_ID = "RESTORED";
  @NonNls public static final String CHANGED_ON_SERVER_ID = "CHANGED_ON_SERVER";
  @NonNls public static final String SKIPPED_ID = "SKIPPED";
  @NonNls public static final String SWITCHED_ID = "SWITCHED";

  /**
   * @param updateName       - Name for "update" action
   * @param statusName       - Name for "status action"
   * @param supportsDeletion - User can perform delete action for files from the group
   * @param id               - Using in order to find the group
   * @param canBeAbsent      - If canBeAbsent == true absent files from the group will not be marked as invalid
   */
  public FileGroup(String updateName, String statusName, boolean supportsDeletion, String id, boolean canBeAbsent) {
    mySupportsDeletion = supportsDeletion;
    myId = id;
    myCanBeAbsent = canBeAbsent;
    myUpdateName = updateName;
    myStatusName = statusName;
  }

  public FileGroup() {
  }

  public void addChild(FileGroup child) {
    myChildren.add(child);
  }

  public boolean getSupportsDeletion() {
    return mySupportsDeletion;
  }

  public void addError(@NotNull final String path, @NotNull final String error) {
    myErrorsMap.put(path, error);
  }

  @NotNull
  public Map<String, String> getErrorsMap() {
    return myErrorsMap;
  }

  public void add(@NotNull String path, @NotNull String vcsName, @Nullable VcsRevisionNumber revision) {
    myFiles.add(new UpdatedFile(path, vcsName, revision == null ? "" : revision.asString()));
  }

  public void add(@NotNull String path, @NotNull VcsKey vcsKey, @Nullable VcsRevisionNumber revision) {
    myFiles.add(new UpdatedFile(path, vcsKey, revision == null ? "" : revision.asString()));
  }

  public void remove(String path) {
    for (UpdatedFile file : myFiles) {
      if (file.getPath().equals(path)) {
        myFiles.remove(file);
        break;
      }
    }
  }

  public int getImmediateFilesSize() {
    return myFiles.size();
  }

  public Collection<String> getFiles() {
    ArrayList<String> files = new ArrayList<>();
    for (UpdatedFile file : myFiles) {
      files.add(file.getPath());
    }
    return files;
  }

  public Collection<UpdatedFile> getUpdatedFiles() {
    return new ArrayList<>(myFiles);
  }

  public List<Pair<String, VcsRevisionNumber>> getFilesAndRevisions(ProjectLevelVcsManager vcsManager) {
    ArrayList<Pair<String, VcsRevisionNumber>> files = new ArrayList<>();
    for (UpdatedFile file : myFiles) {
      VcsRevisionNumber number = getRevision(vcsManager, file);
      files.add(Pair.create(file.getPath(), number));
    }
    return files;
  }

  public boolean isEmpty() {
    if (!myFiles.isEmpty()) return false;
    for (FileGroup child : myChildren) {
      if (!child.isEmpty()) return false;
    }
    return true;
  }

  public SimpleTextAttributes getInvalidAttributes() {
    if (myCanBeAbsent) {
      return new SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, FileStatus.DELETED.getColor());
    }
    else {
      return SimpleTextAttributes.ERROR_ATTRIBUTES;
    }
  }

  public String getId() {
    return myId;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (final UpdatedFile file : myFiles) {
      Element path = new Element(PATH);
      path.setText(file.getPath());
      if (file.getVcsName() != null) {
        path.setAttribute(VCS_ATTRIBUTE, file.getVcsName());
      }
      if (file.getRevision() != null) {
        path.setAttribute(REVISION_ATTRIBUTE, file.getRevision());
      }
      element.addContent(path);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    List pathElements = element.getChildren(PATH);
    for (final Object pathElement1 : pathElements) {
      Element pathElement = (Element)pathElement1;
      final String path = pathElement.getText();
      final String vcsName = pathElement.getAttributeValue(VCS_ATTRIBUTE);
      final String revision = pathElement.getAttributeValue(REVISION_ATTRIBUTE);
      if (vcsName != null) {   // ignore UpdatedFiles from previous version
        myFiles.add(new UpdatedFile(path, vcsName, revision));
      }
    }
  }

  public List<FileGroup> getChildren() {
    return myChildren;
  }

  public static void writeGroupsToElement(List<FileGroup> groups, Element element) throws WriteExternalException {
    for (FileGroup fileGroup : groups) {
      Element groupElement = new Element(FILE_GROUP_ELEMENT_NAME);
      element.addContent(groupElement);
      fileGroup.writeExternal(groupElement);
      writeGroupsToElement(fileGroup.getChildren(), groupElement);
    }
  }

  public static void readGroupsFromElement(List<FileGroup> groups, Element element) throws InvalidDataException {
    List groupElements = element.getChildren();
    for (final Object groupElement1 : groupElements) {
      Element groupElement = (Element)groupElement1;
      FileGroup fileGroup = new FileGroup();
      fileGroup.readExternal(groupElement);
      groups.add(fileGroup);
      readGroupsFromElement(fileGroup.myChildren, groupElement);
    }
  }

  public String getStatusName() {
    return myStatusName;
  }

  public String getUpdateName() {
    return myUpdateName;
  }

  @SuppressWarnings({"HardCodedStringLiteral"})
  public String toString() {
    return myId + " " + myFiles.size() + " items: " + myFiles;
  }

  @Nullable
  public VcsRevisionNumber getRevision(final ProjectLevelVcsManager vcsManager, final String path) {
    for (UpdatedFile file : myFiles) {
      if (file.getPath().equals(path)) {
        return getRevision(vcsManager, file);
      }
    }
    return null;
  }

  @Nullable
  private static VcsRevisionNumber getRevision(final ProjectLevelVcsManager vcsManager, final UpdatedFile file) {
    final String vcsName = file.getVcsName();
    final String revision = file.getRevision();
    if (vcsName != null && revision != null) {
      AbstractVcs vcs = vcsManager.findVcsByName(vcsName);
      if (vcs != null) {
        try {
          return vcs.parseRevisionNumber(revision, VcsUtil.getFilePath(file.getPath()));
        }
        catch (VcsException e) {
          //
        }
      }
    }
    return null;
  }

  /**
   * @deprecated: remove after IDEA 14
   */
  public void setRevisions(final String path, final AbstractVcs vcs, final VcsRevisionNumber revision) {
    for (UpdatedFile file : myFiles) {
      if (file.getPath().startsWith(path)) {
        file.setVcsKey(vcs.getKeyInstanceMethod());
        file.setRevision(revision.asString());
      }
    }
    for (FileGroup group : myChildren) {
      group.setRevisions(path, vcs, revision);
    }
  }

  static class UpdatedFile {
    private final String myPath;
    private String myVcsName;
    private String myRevision;

    public UpdatedFile(final String path) {
      myPath = path;
    }

    public UpdatedFile(final String path, @NotNull final VcsKey vcsKey, final String revision) {
      myPath = path;
      myVcsName = vcsKey.getName();
      myRevision = revision;
    }

    private UpdatedFile(final String path, @NotNull String vcsName, final String revision) {
      myPath = path;
      myVcsName = vcsName;
      myRevision = revision;
    }

    public String getPath() {
      return myPath;
    }

    public String getVcsName() {
      return myVcsName;
    }

    public void setVcsKey(final VcsKey vcsKey) {
      myVcsName = vcsKey.getName();
    }

    public String getRevision() {
      return myRevision;
    }

    public void setRevision(final String revision) {
      myRevision = revision;
    }
  }
}
