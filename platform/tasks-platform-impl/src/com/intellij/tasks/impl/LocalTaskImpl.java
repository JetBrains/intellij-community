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

package com.intellij.tasks.impl;

import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.IconLoader;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.tasks.*;
import com.intellij.tasks.timeTracking.model.WorkItem;
import com.intellij.util.xmlb.annotations.*;
import icons.TasksIcons;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * @author Dmitry Avdeev
*/
@Tag("task")
@SuppressWarnings({"UnusedDeclaration"})
public class LocalTaskImpl extends LocalTask {

  @NonNls public static final String DEFAULT_TASK_ID = "Default";

  private String myId = "";
  private String mySummary = "";
  private String myDescription = null;
  private Comment[] myComments = Comment.EMPTY_ARRAY;
  private boolean myClosed = false;
  private Date myCreated;
  private Date myUpdated;
  private TaskType myType = TaskType.OTHER;
  private String myPresentableName;
  private String myCustomIcon = null;

  private String myProject = null;
  private String myNumber = "";
  private String myPresentableId = "";

  private boolean myIssue = false;
  private TaskRepository myRepository = null;
  private String myIssueUrl = null;

  private boolean myActive;
  private List<ChangeListInfo> myChangeLists = new ArrayList<>();
  private String myShelfName;
  private boolean myRunning = false;
  private List<WorkItem> myWorkItems = new ArrayList<>();
  private Date myLastPost;
  private List<BranchInfo> myBranches = new ArrayList<>();

  /** for serialization */
  public LocalTaskImpl() {    
  }

  public LocalTaskImpl(@NotNull String id, @NotNull String summary) {
    myId = id;
    mySummary = summary;
  }

  public LocalTaskImpl(Task origin) {

    myId = origin.getId();
    myIssue = origin.isIssue();
    myRepository = origin.getRepository();

    copy(origin);

    if (origin instanceof LocalTaskImpl) {
      myChangeLists = ((LocalTaskImpl)origin).getChangeLists();
      myBranches = ((LocalTaskImpl)origin).getBranches();
      myActive = ((LocalTaskImpl)origin).isActive();
      myWorkItems = ((LocalTaskImpl)origin).getWorkItems();
      myRunning = ((LocalTaskImpl)origin).isRunning();
      myLastPost = ((LocalTaskImpl)origin).getLastPost();
    }
  }

  @Attribute("id")
  @NotNull
  public String getId() {
    return myId;
  }

  @Attribute("summary")
  @NotNull
  public String getSummary() {
    return mySummary;
  }

  @Override
  public String getDescription() {
    return myDescription;
  }

  @NotNull
  @Override
  public Comment[] getComments() {
    return myComments;
  }

  @Tag("updated")
  public Date getUpdated() {
    return myUpdated == null ? getCreated() : myUpdated;
  }

  @Tag("created")
  public Date getCreated() {
    if (myCreated == null) {
      myCreated = new Date();
    }
    return myCreated;
  }

  @Attribute("active")
  public boolean isActive() {
    return myActive;
  }

  @Override
  public void updateFromIssue(Task issue) {
    copy(issue);
    myIssue = true;
  }

  private void copy(Task issue) {
    mySummary = issue.getSummary();
    myDescription = issue.getDescription();
    myComments = issue.getComments();
    myClosed = issue.isClosed();
    myCreated = issue.getCreated();
    if (Comparing.compare(myUpdated, issue.getUpdated()) < 0) {
      myUpdated = issue.getUpdated();
    }
    myType = issue.getType();
    myPresentableName = issue.getPresentableName();
    myCustomIcon = issue.getCustomIcon();
    myIssueUrl = issue.getIssueUrl();
    myRepository = issue.getRepository();

    myProject = issue.getProject();
    myNumber = issue.getNumber();
    myPresentableId = issue.getPresentableId();
  }

  public void setId(String id) {
    myId = id;
  }

  public void setSummary(String summary) {
    mySummary = summary;
  }

  public void setActive(boolean active) {
    myActive = active;
  }

  @Override
  public boolean isIssue() {
    return myIssue;
  }

  @Tag("url")
  @Override
  public String getIssueUrl() {
    return myIssueUrl;
  }

  public String setIssueUrl(String url) {
    return myIssueUrl = url;
  }

  public void setIssue(boolean issue) {
    myIssue = issue;
  }

  @Transient
  @Override
  public TaskRepository getRepository() {
    return myRepository;
  }

  public void setRepository(TaskRepository repository) {
    myRepository = repository;
  }

  public void setCreated(Date created) {
    myCreated = created;
  }

  public void setUpdated(Date updated) {
    myUpdated = updated;
  }

  @NotNull
  @Property(surroundWithTag = false)
  @XCollection(elementName="changelist")
  public List<ChangeListInfo> getChangeLists() {
    return myChangeLists;
  }

  // for serialization
  public void setChangeLists(List<ChangeListInfo> changeLists) {
    myChangeLists = changeLists;
  }

  @Override
  public void addChangelist(final ChangeListInfo info) {
    if (!myChangeLists.contains(info)) {
      myChangeLists.add(info);
    }
  }

  @Override
  public void removeChangelist(final ChangeListInfo info) {
    myChangeLists.remove(info);
  }


  @NotNull
  @Override
  @Property(surroundWithTag = false)
  @XCollection(elementName="branch")
  public List<BranchInfo> getBranches() {
    return myBranches;
  }

  public void setBranches(List<BranchInfo> branches) {
    myBranches = branches;
  }

  @Override
  public void addBranch(BranchInfo info) {
    myBranches.add(info);
  }

  @Override
  public void removeBranch(BranchInfo info) {
    myBranches.add(info);
  }

  @Override
  public String getShelfName() {
    return myShelfName;
  }

  @Override
  public void setShelfName(String shelfName) {
    myShelfName = shelfName;
  }

  public boolean isClosed() {
    return myClosed;
  }

  public void setClosed(boolean closed) {
    myClosed = closed;
  }

  @NotNull
  @Override
  public Icon getIcon() {
    final String customIcon = getCustomIcon();
    if (customIcon != null && myRepository != null) {
      // Load icon in the classloader of the corresponding repository implementation.
      // Fallback to the platform icons if the repository wasn't found.
      return IconLoader.getIcon(customIcon, myRepository.getClass());
    }
    return getIconFromType(myType, isIssue());
  }

  public static Icon getIconFromType(TaskType type, boolean issue) {
    switch (type) {
      case BUG:
        return TasksIcons.Bug;
      case EXCEPTION:
        return TasksIcons.Exception;
      case FEATURE:
        return TasksIcons.Feature;
      default:
      case OTHER:
        return issue ? TasksIcons.Other : TasksIcons.Unknown;
    }
  }

  @NotNull
  @Override
  public TaskType getType() {
    return myType;
  }

  public void setType(TaskType type) {
    myType = type == null ? TaskType.OTHER : type;
  }

  @Override
  public boolean isDefault() {
    return myId.equals(DEFAULT_TASK_ID);
  }

  @Override
  public String getPresentableName() {
    return myPresentableName != null ? myPresentableName : toString();
  }

  public String getCustomIcon() {
    return myCustomIcon;
  }

  public long getTotalTimeSpent() {
    long timeSpent = 0;
    for (WorkItem item : myWorkItems) {
      timeSpent += item.duration;
    }
    return timeSpent;
  }

  @Tag("running")
  @Override
  public boolean isRunning() {
    return myRunning;
  }

  public void setRunning(final boolean running) {
    myRunning = running;
  }

  @Override
  public void setWorkItems(final List<WorkItem> workItems) {
    myWorkItems = workItems;
  }

  @NotNull
  @Property(surroundWithTag = false)
  @XCollection(elementName = "workItem")
  @Override
  public List<WorkItem> getWorkItems() {
    return myWorkItems;
  }

  @Override
  public void addWorkItem(final WorkItem workItem) {
    myWorkItems.add(workItem);
  }

  @Tag("lastPost")
  @Override
  public Date getLastPost() {
    return myLastPost;
  }

  @Override
  public void setLastPost(final Date date) {
    myLastPost = date;
  }

  @Override
  public long getTimeSpentFromLastPost() {
    long timeSpent = 0;
    if (myLastPost != null) {
      for (WorkItem item : myWorkItems) {
        if (item.from.getTime() < myLastPost.getTime()) {
          if (item.from.getTime() + item.duration > myLastPost.getTime()) {
            timeSpent += item.from.getTime() + item.duration - myLastPost.getTime();
          }
        }
        else {
          timeSpent += item.duration;
        }
      }
    }
    else {
      for (WorkItem item : myWorkItems) {
        timeSpent += item.duration;
      }
    }
    return timeSpent;
  }

  @NotNull
  @Override
  public String getNumber() {
    // extract number from ID for compatibility
    return StringUtil.isEmpty(myNumber) ? extractNumberFromId(myId) : myNumber;
  }

  public void setNumber(@NotNull String number) {
    myNumber = number;
  }

  @Nullable
  @Override
  public String getProject() {
    // extract project from ID for compatibility
    return StringUtil.isEmpty(myProject) ? extractProjectFromId(myId) : myProject;
  }

  public void setProject(@Nullable String project) {
    myProject = project;
  }

  public void setPresentableId(@NotNull String presentableId) {
    myPresentableId = presentableId;
  }

  @NotNull
  @Override
  public String getPresentableId() {
    // Use global ID for compatibility
    return StringUtil.isEmpty(myPresentableId) ? getId() : myPresentableId;
  }
}
