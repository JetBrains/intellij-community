// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.io.mandatory.Mandatory;
import org.jetbrains.io.mandatory.RestModel;

//region Issue label
/*{
    "id": 208045946,
    "node_id": "MDU6TGFiZWwyMDgwNDU5NDY=",
    "url": "https://api.github.com/repos/octocat/Hello-World/labels/bug",
    "name": "bug",
    "description": "Houston, we have a problem",
    "color": "f29513",
    "default": true
  }*/
//endregion
@RestModel
@SuppressWarnings("UnusedDeclaration")
public class GithubIssueLabel {
  private Long id;
  private String nodeId;
  private String url;
  @Mandatory private String name;
  private String description;
  @Mandatory private String color;

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getColor() {
    return color;
  }
}
