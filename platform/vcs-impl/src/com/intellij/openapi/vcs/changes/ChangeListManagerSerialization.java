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
package com.intellij.openapi.vcs.changes;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.FilePath;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.vcsUtil.VcsUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.*;

class ChangeListManagerSerialization {
  @NonNls private static final String ATT_ID = "id";
  @NonNls private static final String ATT_NAME = "name";
  @NonNls private static final String ATT_COMMENT = "comment";
  @NonNls private static final String ATT_DEFAULT = "default";
  @NonNls private static final String ATT_READONLY = "readonly";
  @NonNls private static final String ATT_VALUE_TRUE = "true";
  @NonNls private static final String ATT_CHANGE_BEFORE_PATH = "beforePath";
  @NonNls private static final String ATT_CHANGE_AFTER_PATH = "afterPath";
  @NonNls private static final String ATT_CHANGE_BEFORE_PATH_IS_DIR = "beforeDir";
  @NonNls private static final String ATT_CHANGE_AFTER_PATH_IS_DIR = "afterDir";
  @NonNls private static final String ATT_PATH = "path";
  @NonNls private static final String ATT_MASK = "mask";
  @NonNls private static final String NODE_LIST = "list";
  @NonNls private static final String NODE_IGNORED = "ignored";
  @NonNls private static final String NODE_CHANGE = "change";
  @NonNls private static final String MANUALLY_REMOVED_FROM_IGNORED = "manually-removed-from-ignored";
  @NonNls private static final String DIRECTORY_TAG = "directory";

  public static void writeExternal(@NotNull Element element, @NotNull IgnoredFilesComponent ignoredFilesComponent, @NotNull ChangeListWorker worker) {
    for (LocalChangeList list : worker.getChangeLists()) {
      element.addContent(writeChangeList(list));
    }

    for (IgnoredFileBean bean : ignoredFilesComponent.getFilesToIgnore()) {
      element.addContent(writeFileToIgnore(bean));
    }

    Set<String> manuallyRemovedFromIgnored = ignoredFilesComponent.getDirectoriesManuallyRemovedFromIgnored();
    if (!manuallyRemovedFromIgnored.isEmpty()) {
      Element list = new Element(MANUALLY_REMOVED_FROM_IGNORED);
      for (String path : manuallyRemovedFromIgnored) {
        list.addContent(new Element(DIRECTORY_TAG).setAttribute(ATT_PATH, path));
      }
      element.addContent(list);
    }
  }

  public static void readExternal(@NotNull Element element, @NotNull IgnoredFilesComponent ignoredIdeaLevel, @NotNull ChangeListWorker worker) {
    List<LocalChangeListImpl> lists = new ArrayList<>();
    for (Element listNode : element.getChildren(NODE_LIST)) {
      lists.add(readChangeList(listNode, worker.getProject()));
    }
    worker.setChangeLists(removeDuplicatedLists(lists));

    ignoredIdeaLevel.clear();
    for (Element ignoredNode : element.getChildren(NODE_IGNORED)) {
      readFileToIgnore(ignoredNode, worker.getProject(), ignoredIdeaLevel);
    }

    Element manuallyRemovedFromIgnoredTag = element.getChild(MANUALLY_REMOVED_FROM_IGNORED);
    Set<String> manuallyRemovedFromIgnoredPaths = new HashSet<>();
    if (manuallyRemovedFromIgnoredTag != null) {
      for (Element tag : manuallyRemovedFromIgnoredTag.getChildren(DIRECTORY_TAG)) {
        manuallyRemovedFromIgnoredPaths.add(tag.getAttributeValue(ATT_PATH));
      }
    }
    ignoredIdeaLevel.setDirectoriesManuallyRemovedFromIgnored(manuallyRemovedFromIgnoredPaths);
  }

  @NotNull
  private static Collection<LocalChangeListImpl> removeDuplicatedLists(@NotNull List<LocalChangeListImpl> lists) {
    // workaround for loading incorrect settings (with duplicate changelist names)

    boolean hasDefault = false;
    Map<String, LocalChangeListImpl> map = new HashMap<>();

    for (LocalChangeListImpl list : lists) {
      if (list.isDefault() && hasDefault) {
        list = new LocalChangeListImpl.Builder(list).setDefault(false).build();
      }
      hasDefault |= list.isDefault();

      LocalChangeListImpl otherList = map.get(list.getName());
      if (otherList != null) {
        list = new LocalChangeListImpl.Builder(otherList)
          .setChanges(ContainerUtil.union(list.getChanges(), otherList.getChanges()))
          .setDefault(list.isDefault() || otherList.isDefault())
          .build();
      }

      map.put(list.getName(), list);
    }
    return map.values();
  }

  @NotNull
  private static Element writeChangeList(@NotNull LocalChangeList list) {
    Element listNode = new Element(NODE_LIST);

    if (list.isDefault()) listNode.setAttribute(ATT_DEFAULT, ATT_VALUE_TRUE);
    if (list.isReadOnly()) listNode.setAttribute(ATT_READONLY, ATT_VALUE_TRUE);

    listNode.setAttribute(ATT_ID, list.getId());
    listNode.setAttribute(ATT_NAME, list.getName());
    String comment = list.getComment();
    if (comment != null) {
      listNode.setAttribute(ATT_COMMENT, comment);
    }

    Object listData = list.getData();
    if (listData instanceof ChangeListData) {
      listNode.addContent(ChangeListData.writeExternal((ChangeListData)listData));
    }
    
    List<Change> changes = ContainerUtil.sorted(list.getChanges(), new ChangeComparator());
    for (Change change : changes) {
      listNode.addContent(writeChange(change));
    }

    return listNode;
  }

  private static class ChangeComparator implements Comparator<Change> {
    @Override
    public int compare(Change o1, Change o2) {
      ContentRevision bRev1 = o1.getBeforeRevision();
      ContentRevision bRev2 = o2.getBeforeRevision();
      int delta = compareRevisions(bRev1, bRev2);
      if (delta != 0) return delta;

      ContentRevision aRev1 = o1.getAfterRevision();
      ContentRevision aRev2 = o2.getAfterRevision();
      return compareRevisions(aRev1, aRev2);
    }

    private static int compareRevisions(@Nullable ContentRevision bRev1, @Nullable ContentRevision bRev2) {
      if (bRev1 == null && bRev2 == null) return 0;
      if (bRev1 == null) return -1;
      if (bRev2 == null) return 1;
      String path1 = bRev1.getFile().getPath();
      String path2 = bRev2.getFile().getPath();
      return path1.compareTo(path2);
    }
  }

  @NotNull
  private static LocalChangeListImpl readChangeList(@NotNull Element listNode, @NotNull Project project) {
    String id = listNode.getAttributeValue(ATT_ID);
    String name = StringUtil.notNullize(listNode.getAttributeValue(ATT_NAME), LocalChangeList.DEFAULT_NAME);
    String comment = StringUtil.notNullize(listNode.getAttributeValue(ATT_COMMENT));
    ChangeListData data = ChangeListData.readExternal(listNode);
    boolean isDefault = ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_DEFAULT));
    boolean isReadOnly = ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_READONLY));

    List<Change> changes = new ArrayList<>();
    for (Element changeNode : listNode.getChildren(NODE_CHANGE)) {
      changes.add(readChange(changeNode));
    }

    return new LocalChangeListImpl.Builder(project, name)
      .setId(id)
      .setComment(comment)
      .setChanges(changes)
      .setData(data)
      .setDefault(isDefault)
      .setReadOnly(isReadOnly)
      .build();
  }

  @NotNull
  private static Element writeFileToIgnore(@NotNull IgnoredFileBean bean) {
    Element fileNode = new Element(NODE_IGNORED);
    String path = bean.getPath();
    if (path != null) {
      fileNode.setAttribute(ATT_PATH, path);
    }
    String mask = bean.getMask();
    if (mask != null) {
      fileNode.setAttribute(ATT_MASK, mask);
    }
    return fileNode;
  }

  private static void readFileToIgnore(@NotNull Element ignoredNode, @NotNull Project project, @NotNull IgnoredFilesComponent ignoredFilesComponent) {
    String path = ignoredNode.getAttributeValue(ATT_PATH);
    if (path != null) {
      ignoredFilesComponent.add(path.endsWith("/") || path.endsWith(File.separator)
                                ? IgnoredBeanFactory.ignoreUnderDirectory(path, project)
                                : IgnoredBeanFactory.ignoreFile(path, project));
    }
    String mask = ignoredNode.getAttributeValue(ATT_MASK);
    if (mask != null) {
      ignoredFilesComponent.add(IgnoredBeanFactory.withMask(mask));
    }
  }

  @NotNull
  private static Element writeChange(@NotNull Change change) {
    Element changeNode = new Element(NODE_CHANGE);
    writeContentRevision(changeNode, change.getBeforeRevision(), RevisionSide.BEFORE);
    writeContentRevision(changeNode, change.getAfterRevision(), RevisionSide.AFTER);
    return changeNode;
  }

  @NotNull
  private static Change readChange(@NotNull Element changeNode) {
    FakeRevision bRev = readContentRevision(changeNode, RevisionSide.BEFORE);
    FakeRevision aRev = readContentRevision(changeNode, RevisionSide.AFTER);
    return new Change(bRev, aRev);
  }

  private static void writeContentRevision(@NotNull Element changeNode, @Nullable ContentRevision rev, @NotNull RevisionSide side) {
    if (rev == null) return;
    FilePath filePath = rev.getFile();
    changeNode.setAttribute(side.getPathKey(), filePath.getPath());
    changeNode.setAttribute(side.getIsDirKey(), String.valueOf(filePath.isDirectory()));
  }

  @Nullable
  private static FakeRevision readContentRevision(@NotNull Element changeNode, @NotNull RevisionSide side) {
    String path = changeNode.getAttributeValue(side.getPathKey());
    if (StringUtil.isEmpty(path)) return null;

    String value = changeNode.getAttributeValue(side.getIsDirKey());
    if (value != null) {
      boolean isDirectory = Boolean.parseBoolean(value);
      return new FakeRevision(VcsUtil.getFilePath(path, isDirectory));
    }
    else {
      // old-style config. Will get "isDirectory" flag from VFS.
      return new FakeRevision(VcsUtil.getFilePath(path));
    }
  }

  private enum RevisionSide {
    BEFORE(ATT_CHANGE_BEFORE_PATH, ATT_CHANGE_BEFORE_PATH_IS_DIR),
    AFTER(ATT_CHANGE_AFTER_PATH, ATT_CHANGE_AFTER_PATH_IS_DIR);

    @NotNull private final String myPathKey;
    @NotNull private final String myIsDirKey;

    RevisionSide(@NotNull String pathKey, @NotNull String isDirKey) {
      myPathKey = pathKey;
      myIsDirKey = isDirKey;
    }

    @NotNull
    public String getPathKey() {
      return myPathKey;
    }

    @NotNull
    public String getIsDirKey() {
      return myIsDirKey;
    }
  }
}
