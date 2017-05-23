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
import com.intellij.util.containers.ContainerUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

class ChangeListManagerSerialization {
  @NonNls private static final String ATT_ID = "id";
  @NonNls private static final String ATT_NAME = "name";
  @NonNls private static final String ATT_COMMENT = "comment";
  @NonNls private static final String ATT_DEFAULT = "default";
  @NonNls private static final String ATT_READONLY = "readonly";
  @NonNls private static final String ATT_VALUE_TRUE = "true";
  @NonNls private static final String ATT_CHANGE_TYPE = "type";
  @NonNls private static final String ATT_CHANGE_BEFORE_PATH = "beforePath";
  @NonNls private static final String ATT_CHANGE_AFTER_PATH = "afterPath";
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
      if (list.isDefault()) {
        if (hasDefault) {
          list.setDefault(false);
        }
        hasDefault = true;
      }

      LocalChangeListImpl otherList = map.get(list.getName());
      if (otherList == null) {
        map.put(list.getName(), list);
      }
      else {
        for (Change change : list.getChanges()) {
          otherList.addChange(change);
        }

        if (list.isDefault()) otherList.setDefault(true);
      }
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

    List<Change> changes = ContainerUtil.sorted(list.getChanges(), Comparator.comparing(Change::toString));
    for (Change change : changes) {
      listNode.addContent(writeChange(change));
    }

    return listNode;
  }

  @NotNull
  private static LocalChangeListImpl readChangeList(@NotNull Element listNode, @NotNull Project project) {
    String id = listNode.getAttributeValue(ATT_ID);
    String name = listNode.getAttributeValue(ATT_NAME);
    String comment = listNode.getAttributeValue(ATT_COMMENT);

    LocalChangeListImpl list = LocalChangeListImpl.createEmptyChangeListImpl(project, name, id);
    list.setCommentImpl(comment);

    for (Element changeNode : listNode.getChildren(NODE_CHANGE)) {
      list.addChange(readChange(changeNode));
    }

    if (ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_DEFAULT))) {
      list.setDefault(true);
    }
    if (ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_READONLY))) {
      list.setReadOnlyImpl(true);
    }

    return list;
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
    changeNode.setAttribute(ATT_CHANGE_TYPE, change.getType().name());

    ContentRevision bRev = change.getBeforeRevision();
    ContentRevision aRev = change.getAfterRevision();

    changeNode.setAttribute(ATT_CHANGE_BEFORE_PATH, bRev != null ? bRev.getFile().getPath() : "");
    changeNode.setAttribute(ATT_CHANGE_AFTER_PATH, aRev != null ? aRev.getFile().getPath() : "");
    return changeNode;
  }

  @NotNull
  private static Change readChange(@NotNull Element changeNode) {
    String bRev = changeNode.getAttributeValue(ATT_CHANGE_BEFORE_PATH);
    String aRev = changeNode.getAttributeValue(ATT_CHANGE_AFTER_PATH);
    return new Change(StringUtil.isEmpty(bRev) ? null : new FakeRevision(bRev),
                      StringUtil.isEmpty(aRev) ? null : new FakeRevision(aRev));
  }
}
