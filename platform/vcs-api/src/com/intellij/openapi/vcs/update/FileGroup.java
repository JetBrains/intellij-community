// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.update;

import com.intellij.openapi.util.DefaultJDOMExternalizer;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.vcs.*;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.ui.SimpleTextAttributes;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.*;

import java.util.*;

public final class FileGroup {
  public @Nls String myUpdateName;
  public @Nls String myStatusName;
  private final Map<String, String> myErrorsMap = new HashMap<>();

  private final Collection<UpdatedFile> myFiles = new ArrayList<>();
  public boolean mySupportsDeletion;
  public boolean myCanBeAbsent;
  public @NonNls String myId;
  private static final @NonNls String PATH = "PATH";
  private static final @NonNls String VCS_ATTRIBUTE = "vcs";
  private static final @NonNls String REVISION_ATTRIBUTE = "revision";

  private final List<FileGroup> myChildren = new ArrayList<>();
  private static final @NonNls String FILE_GROUP_ELEMENT_NAME = "FILE-GROUP";

  public static final @NonNls String MODIFIED_ID = "MODIFIED";
  public static final @NonNls String MERGED_WITH_CONFLICT_ID = "MERGED_WITH_CONFLICTS";
  public static final @NonNls String MERGED_WITH_TREE_CONFLICT = "MERGED_WITH_TREE_CONFLICT";
  public static final @NonNls String MERGED_WITH_PROPERTY_CONFLICT_ID = "MERGED_WITH_PROPERTY_CONFLICT";
  public static final @NonNls String MERGED_ID = "MERGED";
  public static final @NonNls String UNKNOWN_ID = "UNKNOWN";
  public static final @NonNls String LOCALLY_ADDED_ID = "LOCALLY_ADDED";
  public static final @NonNls String LOCALLY_REMOVED_ID = "LOCALLY_REMOVED";
  public static final @NonNls String UPDATED_ID = "UPDATED";
  public static final @NonNls String REMOVED_FROM_REPOSITORY_ID = "REMOVED_FROM_REPOSITORY";
  public static final @NonNls String CREATED_ID = "CREATED";
  public static final @NonNls String RESTORED_ID = "RESTORED";
  public static final @NonNls String CHANGED_ON_SERVER_ID = "CHANGED_ON_SERVER";
  public static final @NonNls String SKIPPED_ID = "SKIPPED";
  public static final @NonNls String SWITCHED_ID = "SWITCHED";

  /**
   * @param updateName       - Name for "update" action
   * @param statusName       - Name for "status action"
   * @param supportsDeletion - User can perform delete action for files from the group
   * @param id               - Using in order to find the group
   * @param canBeAbsent      - If canBeAbsent == true absent files from the group will not be marked as invalid
   */
  public FileGroup(@Nls String updateName, @Nls String statusName, boolean supportsDeletion, @NonNls String id, boolean canBeAbsent) {
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

  public void addError(final @NotNull String path, final @NotNull String error) {
    myErrorsMap.put(path, error);
  }

  public @NotNull Map<String, String> getErrorsMap() {
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
      files.add(new Pair<>(file.getPath(), number));
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

  public @NonNls String getId() {
    return myId;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    DefaultJDOMExternalizer.writeExternal(this, element);
    for (final UpdatedFile file : myFiles) {
      Element path = new Element(PATH);
      path.setText(file.getPath());
      path.setAttribute(VCS_ATTRIBUTE, file.getVcsName());
      if (file.getRevision() != null) {
        path.setAttribute(REVISION_ATTRIBUTE, file.getRevision());
      }
      element.addContent(path);
    }
  }

  public void readExternal(Element element) throws InvalidDataException {
    DefaultJDOMExternalizer.readExternal(this, element);
    List<Element> pathElements = element.getChildren(PATH);
    for (Element pathElement : pathElements) {
      String path = pathElement.getText();
      String vcsName = pathElement.getAttributeValue(VCS_ATTRIBUTE);
      String revision = pathElement.getAttributeValue(REVISION_ATTRIBUTE);
      // ignore UpdatedFiles from a previous version
      if (vcsName != null) {
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

  public static void readGroupsFromElement(List<? super FileGroup> groups, Element element) throws InvalidDataException {
    List<Element> groupElements = element.getChildren();
    for (Element groupElement : groupElements) {
      FileGroup fileGroup = new FileGroup();
      fileGroup.readExternal(groupElement);
      groups.add(fileGroup);
      readGroupsFromElement(fileGroup.myChildren, groupElement);
    }
  }

  public @Nls String getStatusName() {
    return myStatusName;
  }

  public @Nls String getUpdateName() {
    return myUpdateName;
  }

  public String toString() {
    return myId + " " + myFiles.size() + " items: " + myFiles;
  }

  public @Nullable VcsRevisionNumber getRevision(final ProjectLevelVcsManager vcsManager, final String path) {
    for (UpdatedFile file : myFiles) {
      if (file.getPath().equals(path)) {
        return getRevision(vcsManager, file);
      }
    }
    return null;
  }

  private static @Nullable VcsRevisionNumber getRevision(final ProjectLevelVcsManager vcsManager, final UpdatedFile file) {
    final String vcsName = file.getVcsName();
    final String revision = file.getRevision();
    if (revision != null) {
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

  @ApiStatus.Internal
  public static final class UpdatedFile {
    private final String myPath;
    private final @NotNull String myVcsName;
    private final String myRevision;

    UpdatedFile(final String path, final @NotNull VcsKey vcsKey, final String revision) {
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

    public @NotNull String getVcsName() {
      return myVcsName;
    }

    public String getRevision() {
      return myRevision;
    }
  }
}
