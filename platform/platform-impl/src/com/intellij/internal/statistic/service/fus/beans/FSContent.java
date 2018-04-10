// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans;

import com.google.gson.GsonBuilder;
import com.intellij.openapi.application.ApplicationInfo;
import com.intellij.openapi.application.PermanentInstallationID;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public class FSContent {
  public String product;
  public String user;

  public Set<FSSession> sessions = null;

  public static FSContent create() {return new FSContent();}

  public FSContent() {
    product = ApplicationInfo.getInstance().getBuild().getProductCode();
    user = PermanentInstallationID.get();
  }

  @Nullable
  public Set<FSSession> getSessions() {
    return sessions;
  }

  public void addSession(@NotNull FSSession session) {
    if (sessions == null) {
      sessions = ContainerUtil.newHashSet();
    }
    sessions.add(session);
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    FSContent content = (FSContent)o;
    return Objects.equals(product, content.product) &&
           Objects.equals(user, content.user);
  }

  @Override
  public int hashCode() {
    return Objects.hash(product, user);
  }

  public final String asJsonString() {
    return new GsonBuilder().create().toJson(this, FSContent.class);
  }

  public static FSContent fromJson(@NotNull String gson) {
    return new GsonBuilder().create().fromJson(gson, FSContent.class);
  }

  public void removeEmptyData() {
    if (sessions != null) {
      FSSession[] toArray = sessions.toArray(new FSSession[0]);
      for (FSSession session : toArray) {
        session.removeEmptyData();
        List<FSGroup> groups = session.getGroups();
        if (groups == null || groups.isEmpty()) {
          sessions.remove(session);
        }
      }
    }
  }
}
