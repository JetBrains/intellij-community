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

import java.util.Date;

@SuppressWarnings("UnusedDeclaration")
class GithubCommitCommentRaw {
  private String htmlUrl;
  private String url;

  private Long id;
  private String commitId;
  private String path;
  private Long position;
  private Long line;
  private String body;
  private String bodyHtml;

  private GithubUserRaw user;

  private Date createdAt;
  private Date updatedAt;
}
