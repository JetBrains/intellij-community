// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.authentication.accounts;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Property;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.plugins.github.api.GithubServerPath;

import java.util.Objects;
import java.util.UUID;

@Tag("account")
public class GithubAccount {
  @Attribute("id")
  @NotNull private final String myId;
  @Attribute("name")
  @NotNull private String myName;
  @Property(style = Property.Style.ATTRIBUTE, surroundWithTag = false)
  @NotNull private final GithubServerPath myServer;

  // serialization
  @SuppressWarnings("unused")
  private GithubAccount() {
    myId = "";
    myName = "";
    myServer = new GithubServerPath();
  }

  GithubAccount(@NotNull String name, @NotNull GithubServerPath server) {
    myId = UUID.randomUUID().toString();
    myName = name;
    myServer = server;
  }

  @Override
  public String toString() {
    return myServer + "/" + myName;
  }

  @NotNull
  String getId() {
    return myId;
  }

  @NotNull
  public String getName() {
    return myName;
  }

  @Transient
  public void setName(@NotNull String name) {
    myName = name;
  }

  @NotNull
  public GithubServerPath getServer() {
    return myServer;
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof GithubAccount)) return false;
    GithubAccount account = (GithubAccount)o;
    return Objects.equals(myId, account.myId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(myId);
  }
}