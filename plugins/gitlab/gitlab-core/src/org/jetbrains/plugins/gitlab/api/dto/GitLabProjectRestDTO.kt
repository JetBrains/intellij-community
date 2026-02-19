// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.gitlab.api.dto

import com.intellij.openapi.util.NlsSafe
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus

/**
 * @see EXAMPLE_14_0
 */
data class GitLabProjectRestDTO(
  val id: String,
  // can it ever be null? GQL seems to think so
  val namespace: NamespaceDTO?,
  val name: @NlsSafe String,
  val nameWithNamespace: @NlsSafe String,
  val pathWithNamespace: String,
  val httpUrlToRepo: String?,
  val sshUrlToRepo: String?,
  val defaultBranch: String?,
  val removeSourceBranchAfterMerge: Boolean,
  val squashOption: GitLabProjectSquashOptionRest,
) {
  data class NamespaceDTO(
    val id: String,
    val name: String,
    val path: String,
    val fullPath: String,
  )
}

/**
 * Example response from 14.0 put in a field for readability
 *
 * Docs from 18.9, bc 14.0 doesn't have them:
 * id	integer	ID of the project.
 * description	string	Description of the project.
 * description_html	string	Description of the project in HTML format.
 * name	string	Name of the project.
 * name_with_namespace	string	Name of the project with its namespace.
 * path	string	Path of the project.
 * path_with_namespace	string	Path of the project with its namespace.
 * created_at	datetime	Timestamp when the project was created.
 * default_branch	string	Default branch of the project.
 * tag_list	array of strings	Deprecated. Use topics instead. List of tags for the project.
 * topics	array of strings	List of topics for the project.
 * ssh_url_to_repo	string	SSH URL to clone the repository.
 * http_url_to_repo	string	HTTP URL to clone the repository.
 * web_url	string	URL to access the project in a browser.
 * readme_url	string	URL to the project’s README file.
 * forks_count	integer	Number of forks of the project.
 * avatar_url	string	URL to the project’s avatar image.
 * star_count	integer	Number of stars the project has received.
 * last_activity_at	datetime	Timestamp of the last activity in the project.
 * visibility	string	Visibility level of the project. Possible values: private, internal, or public.
 * namespace	object	Namespace information for the project.
 * namespace.id	integer	ID of the namespace.
 * namespace.name	string	Name of the namespace.
 * namespace.path	string	Path of the namespace.
 * namespace.kind	string	Type of namespace. Possible values: user or group.
 * namespace.full_path	string	Full path of the namespace.
 * namespace.parent_id	integer	ID of the parent namespace, if applicable.
 * namespace.avatar_url	string	URL to the namespace’s avatar image.
 * namespace.web_url	string	URL to access the namespace in a browser.
 * container_registry_image_prefix	string	Prefix for container registry images.
 * _links	object	Collection of API endpoint links related to the project.
 * _links.self	string	URL to the project resource.
 * _links.issues	string	URL to the project’s issues.
 * _links.merge_requests	string	URL to the project’s merge requests.
 * _links.repo_branches	string	URL to the project’s repository branches.
 * _links.labels	string	URL to the project’s labels.
 * _links.events	string	URL to the project’s events.
 * _links.members	string	URL to the project’s members.
 * _links.cluster_agents	string	URL to the project’s cluster agents.
 * marked_for_deletion_at	date	Deprecated. Use marked_for_deletion_on instead. Date when the project is scheduled for deletion.
 * marked_for_deletion_on	date	Date when the project is scheduled for deletion.
 * packages_enabled	boolean	Whether the package registry is enabled for the project.
 * empty_repo	boolean	Whether the repository is empty.
 * archived	boolean	Whether the project is archived.
 * owner	object	Information about the project owner.
 * owner.id	integer	ID of the project Owner.
 * owner.username	string	Username of the owner.
 * owner.public_email	string	Public email address of the owner.
 * owner.name	string	Name of the project Owner.
 * owner.state	string	Current state of the owner account.
 * owner.locked	boolean	Indicates if the owner account is locked.
 * owner.avatar_url	string	URL to the owner’s avatar image.
 * owner.web_url	string	Web URL for the owner’s profile.
 * owner.created_at	datetime	Timestamp when the Owner was created.
 * resolve_outdated_diff_discussions	boolean	Whether outdated diff discussions are automatically resolved.
 * container_expiration_policy	object	Settings for container image expiration policy.
 * container_expiration_policy.cadence	string	How often the container expiration policy runs.
 * container_expiration_policy.enabled	boolean	Whether the container expiration policy is enabled.
 * container_expiration_policy.keep_n	integer	Number of container images to keep.
 * container_expiration_policy.older_than	string	Remove container images older than this value.
 * container_expiration_policy.name_regex	string	Deprecated. Use name_regex_delete instead. Regular expression to match container image names.
 * container_expiration_policy.name_regex_delete	string	Regular expression to match container image names to delete.
 * container_expiration_policy.name_regex_keep	string	Regular expression to match container image names to keep.
 * container_expiration_policy.next_run_at	datetime	Timestamp for the next scheduled policy run.
 * repository_object_format	string	Object format used by the repository. Possible values: sha1 or sha256.
 * issues_enabled	boolean	Whether issues are enabled for the project.
 * merge_requests_enabled	boolean	Whether merge requests are enabled for the project.
 * wiki_enabled	boolean	Whether the wiki is enabled for the project.
 * jobs_enabled	boolean	Whether jobs are enabled for the project.
 * snippets_enabled	boolean	Whether snippets are enabled for the project.
 * container_registry_enabled	boolean	Deprecated. Use container_registry_access_level instead. Whether the container registry is enabled.
 * service_desk_enabled	boolean	Whether Service Desk is enabled for the project.
 * service_desk_address	string	Email address for the Service Desk.
 * can_create_merge_request_in	boolean	Whether the current user can create merge requests in the project.
 * issues_access_level	string	Access level for the issues feature. Possible values: disabled, private, or enabled.
 * repository_access_level	string	Access level for the repository feature. Possible values: disabled, private, or enabled.
 * merge_requests_access_level	string	Access level for the merge requests feature. Possible values: disabled, private, or enabled.
 * forking_access_level	string	Access level for forking the project. Possible values: disabled, private, or enabled.
 * wiki_access_level	string	Access level for the wiki feature. Possible values: disabled, private, or enabled.
 * builds_access_level	string	Access level for the CI/CD builds feature. Possible values: disabled, private, or enabled.
 * snippets_access_level	string	Access level for the snippets feature. Possible values: disabled, private, or enabled.
 * pages_access_level	string	Access level for GitLab Pages. Possible values: disabled, private, enabled, or public.
 * analytics_access_level	string	Access level for analytics features. Possible values: disabled, private, or enabled.
 * container_registry_access_level	string	Access level for the container registry. Possible values: disabled, private, or enabled.
 * security_and_compliance_access_level	string	Access level for security and compliance features. Possible values: disabled, private, or enabled.
 * releases_access_level	string	Access level for the releases feature. Possible values: disabled, private, or enabled.
 * environments_access_level	string	Access level for the environments feature. Possible values: disabled, private, or enabled.
 * feature_flags_access_level	string	Access level for the feature flags feature. Possible values: disabled, private, or enabled.
 * infrastructure_access_level	string	Access level for the infrastructure feature. Possible values: disabled, private, or enabled.
 * monitor_access_level	string	Access level for the monitor feature. Possible values: disabled, private, or enabled.
 * model_experiments_access_level	string	Access level for the model experiments feature. Possible values: disabled, private, or enabled.
 * model_registry_access_level	string	Access level for the model registry feature. Possible values: disabled, private, or enabled.
 * package_registry_access_level	string	Access level for the package registry feature. Possible values: disabled, private, or enabled.
 * emails_disabled	boolean	Indicates if emails are disabled for the project.
 * emails_enabled	boolean	Indicates if emails are enabled for the project.
 * show_diff_preview_in_email	boolean	Indicates if diff previews are shown in email notifications.
 * shared_runners_enabled	boolean	Whether shared runners are enabled for the project.
 * lfs_enabled	boolean	Indicates if Git LFS is enabled for the project.
 * creator_id	integer	ID of the user who created the project.
 * import_url	string	URL the project was imported from.
 * import_type	string	Type of import used for the project.
 * import_status	string	Status of the project import.
 * import_error	string	Error message if the import failed.
 * open_issues_count	integer	Number of open issues.
 * updated_at	datetime	Timestamp when the project was last updated.
 * ci_default_git_depth	integer	Default Git depth for CI/CD pipelines. Only visible if you have administrator access or the Owner role for the project.
 * ci_delete_pipelines_in_seconds	integer	Time in seconds before old pipelines are deleted.
 * ci_forward_deployment_enabled	boolean	Whether forward deployment is enabled. Only visible if you have administrator access or the Owner role for the project.
 * ci_forward_deployment_rollback_allowed	boolean	Whether rollback is allowed for forward deployments.
 * ci_job_token_scope_enabled	boolean	Indicates if CI/CD job token scope is enabled. Only visible if you have administrator access or the Owner role for the project.
 * ci_separated_caches	boolean	Whether CI/CD caches are separated by branch. Only visible if you have administrator access or the Owner role for the project.
 * ci_allow_fork_pipelines_to_run_in_parent_project	boolean	Whether fork pipelines can run in the parent project. Only visible if you have administrator access or the Owner role for the project.
 * ci_id_token_sub_claim_components	array of strings	Components included in the CI/CD ID token subject claim.
 * build_git_strategy	string	Git strategy used for CI/CD builds (fetch or clone). Only visible if you have administrator access or the Owner role for the project.
 * keep_latest_artifact	boolean	Indicates if the latest artifact is kept when a new one is created. Only visible if you have administrator access or the Owner role for the project.
 * restrict_user_defined_variables	boolean	Whether user-defined variables are restricted. Only visible if you have administrator access or the Owner role for the project.
 * ci_pipeline_variables_minimum_override_role	string	Minimum role required to override pipeline variables.
 * runner_token_expiration_interval	integer	Expiration interval in seconds for runner tokens. Only visible if you have administrator access or the Owner role for the project.
 * group_runners_enabled	boolean	Whether group runners are enabled for the project. Only visible if you have administrator access or the Owner role for the project.
 * resource_group_default_process_mode	string	Default process mode for resource groups.
 * auto_cancel_pending_pipelines	string	Setting for automatically canceling pending pipelines. Only visible if you have administrator access or the Owner role for the project.
 * build_timeout	integer	Timeout in seconds for CI/CD jobs. Only visible if you have administrator access or the Owner role for the project.
 * auto_devops_enabled	boolean	Whether Auto DevOps is enabled for the project. Only visible if you have administrator access or the Owner role for the project.
 * auto_devops_deploy_strategy	string	Deployment strategy for Auto DevOps. Only visible if you have administrator access or the Owner role for the project.
 * ci_push_repository_for_job_token_allowed	boolean	Whether pushing to the repository is allowed using a job token.
 * runners_token	string	Token for registering runners with the project. Only visible if you have administrator access or the Owner role for the project.
 * ci_config_path	string	Path to the CI/CD configuration file.
 * public_jobs	boolean	Whether job logs are publicly accessible.
 * shared_with_groups	array of objects	List of groups the project is shared with.
 * shared_with_groups[].group_id	integer	ID of the group the project is shared with.
 * shared_with_groups[].group_name	string	Name of the group the project is shared with.
 * shared_with_groups[].group_full_path	string	Full path of the group the project is shared with.
 * shared_with_groups[].group_access_level	integer	Access level granted to the group.
 * only_allow_merge_if_pipeline_succeeds	boolean	Whether merges are allowed only if the pipeline succeeds.
 * allow_merge_on_skipped_pipeline	boolean	Whether merges are allowed when the pipeline is skipped.
 * request_access_enabled	boolean	Whether users can request access to the project.
 * only_allow_merge_if_all_discussions_are_resolved	boolean	Whether merges are allowed only if all discussions are resolved.
 * remove_source_branch_after_merge	boolean	Whether the source branch is automatically removed after merge.
 * printing_merge_request_link_enabled	boolean	Indicates if merge request links are printed after pushing.
 * printing_merge_requests_link_enabled	boolean	Whether the merge request link is printed after a push.
 * merge_method	string	Merge method used for the project. Possible values: merge, rebase_merge, or ff.
 * merge_request_title_regex	string	Regex pattern for validating merge request titles.
 * merge_request_title_regex_description	string	Description of the merge request title regex validation.
 * squash_option	string	Squash option for merge requests.
 * enforce_auth_checks_on_uploads	boolean	Whether authentication checks are enforced on uploads.
 * suggestion_commit_message	string	Custom commit message for suggestions.
 * merge_commit_template	string	Template for merge commit messages.
 * squash_commit_template	string	Template for squash commit messages.
 * issue_branch_template	string	Template for branch names created from issues.
 * warn_about_potentially_unwanted_characters	boolean	Whether to warn about potentially unwanted characters.
 * autoclose_referenced_issues	boolean	Whether referenced issues are automatically closed.
 * max_artifacts_size	integer	Maximum size in MB for CI/CD artifacts.
 * approvals_before_merge	integer	Deprecated. Use merge request approvals API instead. Number of approvals required before merge.
 * mirror	boolean	Whether the project is a mirror.
 * external_authorization_classification_label	string	External authorization classification label.
 * requirements_enabled	boolean	Indicates if requirements management is enabled.
 * requirements_access_level	string	Access level for the requirements feature.
 * security_and_compliance_enabled	boolean	Indicates if security and compliance features are enabled.
 * secret_push_protection_enabled	boolean	Whether secret push protection is enabled.
 * pre_receive_secret_detection_enabled	boolean	Indicates if pre-receive secret detection is enabled.
 * compliance_frameworks	array of strings	Compliance frameworks applied to the project.
 * issues_template	string	Default description for issues. Description is parsed with GitLab Flavored Markdown. Premium and Ultimate only.
 * merge_requests_template	string	Template for merge request descriptions. Premium and Ultimate only.
 * ci_restrict_pipeline_cancellation_role	string	Minimum role required to cancel pipelines.
 * merge_pipelines_enabled	boolean	Indicates if merge pipelines are enabled.
 * merge_trains_enabled	boolean	Indicates if merge trains are enabled.
 * merge_trains_skip_train_allowed	boolean	Indicates if skipping the merge train is allowed.
 * only_allow_merge_if_all_status_checks_passed	boolean	Whether merges are allowed only if all status checks have passed. Ultimate only.
 * allow_pipeline_trigger_approve_deployment	boolean	Whether pipeline triggers can approve deployments.
 * prevent_merge_without_jira_issue	boolean	Indicates if merges require an associated Jira issue.
 * duo_remote_flows_enabled	boolean	Indicates if GitLab Duo remote flows are enabled.
 * duo_foundational_flows_enabled	boolean	Indicates if GitLab Duo foundational flows are enabled.
 * duo_sast_fp_detection_enabled	boolean	Indicates if GitLab Duo SAST false positive detection is enabled.
 * web_based_commit_signing_enabled	boolean	Indicates if web-based commit signing is enabled.
 * spp_repository_pipeline_access	boolean	Repository pipeline access for security policies. Only visible if the security orchestration policies feature is available.
 * permissions	object	User permissions for the project.
 * permissions.project_access	object	Project-level access permissions for the user.
 * permissions.project_access.access_level	integer	Access level for the project.
 * permissions.project_access.notification_level	integer	Notification level for the project.
 * permissions.group_access	object	Group-level access permissions for the user.
 * permissions.group_access.access_level	integer	Access level for the group.
 * permissions.group_access.notification_level	integer	Notification level for the group.
 * license_url	string	URL to the project’s license file.
 * license.key	string	Key identifier for the license.
 * license.name	string	Full name of the license.
 * license.nickname	string	Nickname of the license.
 * license.html_url	string	URL to view the license details.
 * license.source_url	string	URL to the license source text.
 * repository_storage	string	Storage location for the project’s repository.
 * mirror_user_id	integer	ID of the user who set up the mirror.
 * mirror_trigger_builds	boolean	Whether mirror updates trigger builds.
 * only_mirror_protected_branches	boolean	Whether only protected branches are mirrored.
 * mirror_overwrites_diverged_branches	boolean	Whether the mirror overwrites diverged branches.
 * statistics.commit_count	integer	Number of commits in the project.
 * statistics.storage_size	integer	Total storage size in bytes.
 * statistics.repository_size	integer	Repository storage size in bytes.
 * statistics.wiki_size	integer	Wiki storage size in bytes.
 * statistics.lfs_objects_size	integer	LFS objects storage size in bytes.
 * statistics.job_artifacts_size	integer	Job artifacts storage size in bytes.
 * statistics.pipeline_artifacts_size	integer	Pipeline artifacts storage size in bytes.
 * statistics.packages_size	integer	Packages storage size in bytes.
 * statistics.snippets_size	integer	Snippets storage size in bytes.
 * statistics.uploads_size	integer	Uploads storage size in bytes.
 * statistics.container_registry_size	integer	Container registry storage size in bytes. 1
 * forked_from_project	object	The upstream project this project was forked from. If the upstream project is private, an authentication token is required to view this field.
 * mr_default_target_self	boolean	Whether merge requests target this project by default. If false, merge requests target the upstream project. Appears only if the project is a fork.
 */
@ApiStatus.Obsolete
@Suppress("unused")
@Language("JSON")
private val EXAMPLE_14_0 = """
  {
  "id": 3,
  "description": null,
  "default_branch": "master",
  "visibility": "private",
  "ssh_url_to_repo": "git@example.com:diaspora/diaspora-project-site.git",
  "http_url_to_repo": "http://example.com/diaspora/diaspora-project-site.git",
  "web_url": "http://example.com/diaspora/diaspora-project-site",
  "readme_url": "http://example.com/diaspora/diaspora-project-site/blob/master/README.md",
  "tag_list": [
    //deprecated, use `topics` instead
    "example",
    "disapora project"
  ],
  "topics": [
    "example",
    "disapora project"
  ],
  "owner": {
    "id": 3,
    "name": "Diaspora",
    "created_at": "2013-09-30T13:46:02Z"
  },
  "name": "Diaspora Project Site",
  "name_with_namespace": "Diaspora / Diaspora Project Site",
  "path": "diaspora-project-site",
  "path_with_namespace": "diaspora/diaspora-project-site",
  "issues_enabled": true,
  "open_issues_count": 1,
  "merge_requests_enabled": true,
  "jobs_enabled": true,
  "wiki_enabled": true,
  "snippets_enabled": false,
  "can_create_merge_request_in": true,
  "resolve_outdated_diff_discussions": false,
  "container_registry_enabled": false,
  "container_expiration_policy": {
    "cadence": "7d",
    "enabled": false,
    "keep_n": null,
    "older_than": null,
    "name_regex": null,
    // to be deprecated in GitLab 13.0 in favor of `name_regex_delete`
    "name_regex_delete": null,
    "name_regex_keep": null,
    "next_run_at": "2020-01-07T21:42:58.658Z"
  },
  "created_at": "2013-09-30T13:46:02Z",
  "last_activity_at": "2013-09-30T13:46:02Z",
  "creator_id": 3,
  "namespace": {
    "id": 3,
    "name": "Diaspora",
    "path": "diaspora",
    "kind": "group",
    "full_path": "diaspora",
    "avatar_url": "http://localhost:3000/uploads/group/avatar/3/foo.jpg",
    "web_url": "http://localhost:3000/groups/diaspora"
  },
  "import_status": "none",
  "import_error": null,
  "permissions": {
    "project_access": {
      "access_level": 10,
      "notification_level": 3
    },
    "group_access": {
      "access_level": 50,
      "notification_level": 3
    }
  },
  "archived": false,
  "avatar_url": "http://example.com/uploads/project/avatar/3/uploads/avatar.png",
  "license_url": "http://example.com/diaspora/diaspora-client/blob/master/LICENSE",
  "license": {
    "key": "lgpl-3.0",
    "name": "GNU Lesser General Public License v3.0",
    "nickname": "GNU LGPLv3",
    "html_url": "http://choosealicense.com/licenses/lgpl-3.0/",
    "source_url": "http://www.gnu.org/licenses/lgpl-3.0.txt"
  },
  "shared_runners_enabled": true,
  "forks_count": 0,
  "star_count": 0,
  "runners_token": "b8bc4a7a29eb76ea83cf79e4908c2b",
  "ci_default_git_depth": 50,
  "ci_forward_deployment_enabled": true,
  "public_jobs": true,
  "shared_with_groups": [
    {
      "group_id": 4,
      "group_name": "Twitter",
      "group_full_path": "twitter",
      "group_access_level": 30
    },
    {
      "group_id": 3,
      "group_name": "Gitlab Org",
      "group_full_path": "gitlab-org",
      "group_access_level": 10
    }
  ],
  "repository_storage": "default",
  "only_allow_merge_if_pipeline_succeeds": false,
  "allow_merge_on_skipped_pipeline": false,
  "restrict_user_defined_variables": false,
  "only_allow_merge_if_all_discussions_are_resolved": false,
  "remove_source_branch_after_merge": false,
  "printing_merge_requests_link_enabled": true,
  "request_access_enabled": false,
  "merge_method": "merge",
  "squash_option": "default_on",
  "auto_devops_enabled": true,
  "auto_devops_deploy_strategy": "continuous",
  
  // premium only
  "approvals_before_merge": null,
  "issues_template": null,
  "merge_requests_template": null,
  
  "mirror": false,
  "mirror_user_id": 45,
  "mirror_trigger_builds": false,
  "only_mirror_protected_branches": false,
  "mirror_overwrites_diverged_branches": false,
  "external_authorization_classification_label": null,
  "packages_enabled": true,
  "service_desk_enabled": false,
  "service_desk_address": null,
  "autoclose_referenced_issues": true,
  "suggestion_commit_message": null,
  // Deprecated and will be removed in API v5 in favor of marked_for_deletion_on
  "marked_for_deletion_at": "2020-04-03",
  "marked_for_deletion_on": "2020-04-03",
  "compliance_frameworks": [
    "sox"
  ],
  "statistics": {
    "commit_count": 37,
    "storage_size": 1038090,
    "repository_size": 1038090,
    "wiki_size": 0,
    "lfs_objects_size": 0,
    "job_artifacts_size": 0,
    "packages_size": 0,
    "snippets_size": 0
  },
  "container_registry_image_prefix": "registry.example.com/diaspora/diaspora-client",
  // nullable
  "forked_from_project": {
    "id": 13083,
    "description": "GitLab Community Edition",
    "name": "GitLab Community Edition",
    "name_with_namespace": "GitLab.org / GitLab Community Edition",
    "path": "gitlab-foss",
    "path_with_namespace": "gitlab-org/gitlab-foss",
    "created_at": "2013-09-26T06:02:36.000Z",
    "default_branch": "master",
    //deprecated, use `topics` instead
    "tag_list": [],
    "topics": [],
    "ssh_url_to_repo": "git@gitlab.com:gitlab-org/gitlab-foss.git",
    "http_url_to_repo": "https://gitlab.com/gitlab-org/gitlab-foss.git",
    "web_url": "https://gitlab.com/gitlab-org/gitlab-foss",
    "avatar_url": "https://assets.gitlab-static.net/uploads/-/system/project/avatar/13083/logo-extra-whitespace.png",
    "license_url": "https://gitlab.com/gitlab-org/gitlab/-/blob/master/LICENSE",
    "license": {
      "key": "mit",
      "name": "MIT License",
      "nickname": null,
      "html_url": "http://choosealicense.com/licenses/mit/",
      "source_url": "https://opensource.org/licenses/MIT"
    },
    "star_count": 3812,
    "forks_count": 3561,
    "last_activity_at": "2018-01-02T11:40:26.570Z",
    "namespace": {
      "id": 72,
      "name": "GitLab.org",
      "path": "gitlab-org",
      "kind": "group",
      "full_path": "gitlab-org",
      "parent_id": null
    }
  },
  "_links": {
    "self": "http://example.com/api/v4/projects",
    "issues": "http://example.com/api/v4/projects/1/issues",
    "merge_requests": "http://example.com/api/v4/projects/1/merge_requests",
    "repo_branches": "http://example.com/api/v4/projects/1/repository_branches",
    "labels": "http://example.com/api/v4/projects/1/labels",
    "events": "http://example.com/api/v4/projects/1/events",
    "members": "http://example.com/api/v4/projects/1/members"
  }
}
"""