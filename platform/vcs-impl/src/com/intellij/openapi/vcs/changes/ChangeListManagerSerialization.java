/*
 * Copyright 2000-2009 JetBrains s.r.o.
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
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.InvalidDataException;
import com.intellij.openapi.util.WriteExternalException;
import com.intellij.openapi.util.text.StringUtil;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.util.*;

class ChangeListManagerSerialization {
  @NonNls static final String ATT_ID = "id";
  @NonNls static final String ATT_NAME = "name";
  @NonNls static final String ATT_COMMENT = "comment";
  @NonNls static final String ATT_DEFAULT = "default";
  @NonNls static final String ATT_READONLY = "readonly";
  @NonNls static final String ATT_VALUE_TRUE = "true";
  @NonNls static final String ATT_CHANGE_TYPE = "type";
  @NonNls static final String ATT_CHANGE_BEFORE_PATH = "beforePath";
  @NonNls static final String ATT_CHANGE_AFTER_PATH = "afterPath";
  @NonNls static final String ATT_PATH = "path";
  @NonNls static final String ATT_MASK = "mask";
  @NonNls static final String NODE_LIST = "list";
  @NonNls static final String NODE_IGNORED = "ignored";
  @NonNls static final String NODE_CHANGE = "change";
  @NonNls static final String MANUALLY_REMOVED_FROM_IGNORED = "manually-removed-from-ignored";
  @NonNls static final String DIRECTORY_TAG = "directory";

  private final IgnoredFilesComponent myIgnoredIdeaLevel;
  private final ChangeListWorker myWorker;

  ChangeListManagerSerialization(final IgnoredFilesComponent ignoredIdeaLevel, final ChangeListWorker worker) {
    myIgnoredIdeaLevel = ignoredIdeaLevel;
    myWorker = worker;
  }

  @SuppressWarnings({"unchecked"})
  public void readExternal(final Element element) throws InvalidDataException {
    final List<Element> listNodes = element.getChildren(NODE_LIST);
    for (Element listNode : listNodes) {
      readChangeList(listNode);
    }
    final List<Element> ignoredNodes = element.getChildren(NODE_IGNORED);
    for (Element ignoredNode : ignoredNodes) {
      readFileToIgnore(ignoredNode);
    }
    Element manuallyRemovedFromIgnoredTag = element.getChild(MANUALLY_REMOVED_FROM_IGNORED);
    Set<String> manuallyRemovedFromIgnoredPaths = new HashSet<>();
    if (manuallyRemovedFromIgnoredTag != null) {
      for (Element tag : manuallyRemovedFromIgnoredTag.getChildren(DIRECTORY_TAG)) {
        manuallyRemovedFromIgnoredPaths.add(tag.getAttributeValue(ATT_PATH));
      }
    }
    myIgnoredIdeaLevel.setDirectoriesManuallyRemovedFromIgnored(manuallyRemovedFromIgnoredPaths);
  }

  private void readChangeList(final Element listNode) {
    // workaround for loading incorrect settings (with duplicate changelist names)
    final String changeListName = listNode.getAttributeValue(ATT_NAME);
    LocalChangeList list = myWorker.getCopyByName(changeListName);
    if (list == null) {
      list = myWorker.addChangeList(listNode.getAttributeValue(ATT_ID), changeListName, listNode.getAttributeValue(ATT_COMMENT), false,
                                    null);
    }
    //noinspection unchecked
    final List<Element> changeNodes = listNode.getChildren(NODE_CHANGE);
    for (Element changeNode : changeNodes) {
      try {
        myWorker.addChangeToList(changeListName, readChange(changeNode), null);
      }
      catch (OutdatedFakeRevisionException e) {
        // Do nothing. Just skip adding outdated revisions to the list.
      }
    }

    if (ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_DEFAULT))) {
      myWorker.setDefault(list.getName());
    }
    if (ATT_VALUE_TRUE.equals(listNode.getAttributeValue(ATT_READONLY))) {
      list.setReadOnly(true);
    }
  }

  private void readFileToIgnore(final Element ignoredNode) {
    String path = ignoredNode.getAttributeValue(ATT_PATH);
    if (path != null) {
      Project project = myWorker.getProject();
      final IgnoredFileBean bean = path.endsWith("/") || path.endsWith(File.separator)
                                   ? IgnoredBeanFactory.ignoreUnderDirectory(path, project)
                                   : IgnoredBeanFactory.ignoreFile(path, project);
      myIgnoredIdeaLevel.add(bean);
    }
    String mask = ignoredNode.getAttributeValue(ATT_MASK);
    if (mask != null) {
      final IgnoredFileBean bean = IgnoredBeanFactory.withMask(mask);
      myIgnoredIdeaLevel.add(bean);
    }
  }

  public void writeExternal(Element element) throws WriteExternalException {
    final List<LocalChangeList> changeListList = myWorker.getListsCopy();
    for (LocalChangeList list : changeListList) {
      Element listNode = new Element(NODE_LIST);
      element.addContent(listNode);
      if (list.isDefault()) {
        listNode.setAttribute(ATT_DEFAULT, ATT_VALUE_TRUE);
      }
      if (list.isReadOnly()) {
        listNode.setAttribute(ATT_READONLY, ATT_VALUE_TRUE);
      }

      listNode.setAttribute(ATT_ID, list.getId());
      listNode.setAttribute(ATT_NAME, list.getName());
      String comment = list.getComment();
      if (comment != null) {
        listNode.setAttribute(ATT_COMMENT, comment);
      }
      List<Change> changes = new ArrayList<>(list.getChanges());
      Collections.sort(changes, new ChangeComparator());
      for (Change change : changes) {
        writeChange(listNode, change);
      }
    }
    final IgnoredFileBean[] filesToIgnore = myIgnoredIdeaLevel.getFilesToIgnore();
    for (IgnoredFileBean bean : filesToIgnore) {
      Element fileNode = new Element(NODE_IGNORED);
      element.addContent(fileNode);
      String path = bean.getPath();
      if (path != null) {
        fileNode.setAttribute("path", path);
      }
      String mask = bean.getMask();
      if (mask != null) {
        fileNode.setAttribute("mask", mask);
      }
    }
    Set<String> manuallyRemovedFromIgnored = myIgnoredIdeaLevel.getDirectoriesManuallyRemovedFromIgnored();
    if (!manuallyRemovedFromIgnored.isEmpty()) {
      Element list = new Element(MANUALLY_REMOVED_FROM_IGNORED);
      for (String path : manuallyRemovedFromIgnored) {
        list.addContent(new Element(DIRECTORY_TAG).setAttribute(ATT_PATH, path));
      }
      element.addContent(list);
    }
  }

  private static class ChangeComparator implements Comparator<Change> {
    @Override
    public int compare(@NotNull Change o1, @NotNull Change o2) {
      return Comparing.compare(o1.toString(), o2.toString());
    }
  }

  private static void writeChange(final Element listNode, final Change change) {
    Element changeNode = new Element(NODE_CHANGE);
    listNode.addContent(changeNode);
    changeNode.setAttribute(ATT_CHANGE_TYPE, change.getType().name());

    final ContentRevision bRev = change.getBeforeRevision();
    final ContentRevision aRev = change.getAfterRevision();

    changeNode.setAttribute(ATT_CHANGE_BEFORE_PATH, bRev != null ? bRev.getFile().getPath() : "");
    changeNode.setAttribute(ATT_CHANGE_AFTER_PATH, aRev != null ? aRev.getFile().getPath() : "");
  }

  private static Change readChange(Element changeNode) throws OutdatedFakeRevisionException {
    String bRev = changeNode.getAttributeValue(ATT_CHANGE_BEFORE_PATH);
    String aRev = changeNode.getAttributeValue(ATT_CHANGE_AFTER_PATH);
    return new Change(StringUtil.isEmpty(bRev) ? null : new FakeRevision(bRev), StringUtil.isEmpty(aRev) ? null : new FakeRevision(aRev));
  }

  static final class OutdatedFakeRevisionException extends Exception {}
}
