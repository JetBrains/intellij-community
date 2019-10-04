// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.api.data;

import org.jetbrains.annotations.NotNull;

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
@SuppressWarnings("UnusedDeclaration")
public class GithubIssueLabel {
  private Long id;
  private String nodeId;
  private String url;
  private String name;
  private String description;
  private String color;

  @NotNull
  public String getNodeId() {
    return nodeId;
  }

  @NotNull
  public String getUrl() {
    return url;
  }

  @NotNull
  public String getName() {
    return name;
  }

  @NotNull
  public String getColor() {
    return color;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubIssueLabel)) return false;

    GithubIssueLabel label = (GithubIssueLabel)o;

    if (!id.equals(label.id)) return false;

    return true;
  }

  @Override
  public int hashCode() {
    return id.hashCode();
  }
}
