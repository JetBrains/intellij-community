// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo
import com.intellij.collaboration.api.dto.GraphQLFragment
import org.jetbrains.plugins.gitlab.api.SinceGitLab
import org.jetbrains.plugins.gitlab.api.dto.GitLabWorkItemDTO.GitLabWidgetDTO.*

@SinceGitLab("14.8")
@GraphQLFragment("graphql/fragment/workItem.graphql")
data class GitLabWorkItemDTO(
  val workItemType: WorkItemType,
  val widgets: List<GitLabWidgetDTO>
) {
  data class WorkItemType(val name: String) {
    companion object {
      const val ISSUE_TYPE = "Issue"
    }
  }

  @JsonTypeInfo(
    use = JsonTypeInfo.Id.NAME,
    include = JsonTypeInfo.As.PROPERTY,
    property = "__typename",
    visible = false,
    defaultImpl = Unknown::class
  )
  @JsonSubTypes(
    JsonSubTypes.Type(name = "WorkItemWidgetAssignees", value = WorkItemWidgetAssignees::class),
    JsonSubTypes.Type(name = "WorkItemWidgetAwardEmoji", value = WorkItemWidgetAwardEmoji::class),
    JsonSubTypes.Type(name = "WorkItemWidgetCurrentUserTodos", value = WorkItemWidgetCurrentUserTodos::class),
    JsonSubTypes.Type(name = "WorkItemWidgetDescription", value = WorkItemWidgetDescription::class),
    JsonSubTypes.Type(name = "WorkItemWidgetHierarchy", value = WorkItemWidgetHierarchy::class),
    JsonSubTypes.Type(name = "WorkItemWidgetLabels", value = WorkItemWidgetLabels::class),
    JsonSubTypes.Type(name = "WorkItemWidgetLinkedItems", value = WorkItemWidgetLinkedItems::class),
    JsonSubTypes.Type(name = "WorkItemWidgetMilestone", value = WorkItemWidgetMilestone::class),
    JsonSubTypes.Type(name = "WorkItemWidgetNotes", value = WorkItemWidgetNotes::class),
    JsonSubTypes.Type(name = "WorkItemWidgetNotifications", value = WorkItemWidgetNotifications::class),
    JsonSubTypes.Type(name = "WorkItemWidgetStartAndDueDate", value = WorkItemWidgetStartAndDueDate::class),
  )
  @SinceGitLab("15.1")
  interface GitLabWidgetDTO {
    @SinceGitLab("15.2")
    @GraphQLFragment("graphql/fragment/workItemWidgetAssignees.graphql")
    data class WorkItemWidgetAssignees(val allowsMultipleAssignees: Boolean) : GitLabWidgetDTO
    class WorkItemWidgetAwardEmoji : GitLabWidgetDTO
    class WorkItemWidgetCurrentUserTodos : GitLabWidgetDTO
    class WorkItemWidgetDescription : GitLabWidgetDTO
    class WorkItemWidgetHierarchy : GitLabWidgetDTO
    class WorkItemWidgetLabels : GitLabWidgetDTO
    class WorkItemWidgetLinkedItems : GitLabWidgetDTO
    class WorkItemWidgetMilestone : GitLabWidgetDTO
    class WorkItemWidgetNotes : GitLabWidgetDTO
    class WorkItemWidgetNotifications : GitLabWidgetDTO
    class WorkItemWidgetStartAndDueDate : GitLabWidgetDTO
    class Unknown : GitLabWidgetDTO
  }
}