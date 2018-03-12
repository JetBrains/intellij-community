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
package org.jetbrains.plugins.github.api.data;

import com.intellij.openapi.util.text.StringUtil;
import com.intellij.util.ObjectUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

import java.util.Date;
import java.util.List;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubPullRequest {
  @Mandatory private Long number;
  @Mandatory private String state;
  @Mandatory private String title;
  private String body;
  private String bodyHtml;

  @Mandatory private Date createdAt;
  @Mandatory private Date updatedAt;
  private Date closedAt;
  private Date mergedAt;

  private GithubUser user;
  @Mandatory private List<GithubUser> assignees;

  public long getNumber() {
    return number;
  }

  @NotNull
  public String getState() {
    return state;
  }

  @NotNull
  public String getTitle() {
    return title;
  }

  @NotNull
  public String getBodyHtml() {
    return StringUtil.notNullize(bodyHtml);
  }

  @NotNull
  public Date getCreatedAt() {
    return createdAt;
  }

  @NotNull
  public Date getUpdatedAt() {
    return updatedAt;
  }

  @Nullable
  public Date getClosedAt() {
    return closedAt;
  }

  @Nullable
  public Date getMergedAt() {
    return mergedAt;
  }

  @NotNull
  public GithubUser getUser() {
    return ObjectUtils.notNull(user, GithubUser.UNKNOWN);
  }

  @NotNull
  public List<GithubUser> getAssignees() {
    return assignees;
  }

  public boolean isClosed() {
    return "closed".equals(state);
  }
}
