// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubIssueCommentWithHtml extends GithubIssueComment {
  @Mandatory private String bodyHtml;

  @NotNull
  public String getBodyHtml() {
    return bodyHtml;
  }
}
