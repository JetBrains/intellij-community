/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.github.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Date;

/**
 * @author Aleksey Pivovarov
 */
@SuppressWarnings("UnusedDeclaration")
class GithubIssueCommentRaw implements DataConstructor {
  @Nullable public Long id;

  @Nullable public String url;
  @Nullable public String htmlUrl;
  @Nullable public String body;
  @Nullable public String bodyHtml;

  @Nullable public Date createdAt;
  @Nullable public Date updatedAt;

  @Nullable public GithubUserRaw user;

  @SuppressWarnings("ConstantConditions")
  @NotNull
  public GithubIssueComment createIssueComment() {
    return new GithubIssueComment(id, htmlUrl, bodyHtml, createdAt, updatedAt, user.createUser());
  }

  @SuppressWarnings("unchecked")
  @NotNull
  @Override
  public <T> T create(@NotNull Class<T> resultClass) {
    if (resultClass == GithubIssueComment.class) {
      return (T)createIssueComment();
    }

    throw new ClassCastException(this.getClass().getName() + ": bad class type: " + resultClass.getName());
  }
}
