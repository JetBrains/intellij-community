// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.service.fus.beans.legacy;

import com.google.gson.GsonBuilder;
import com.intellij.internal.statistic.service.fus.beans.*;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.Map;
import java.util.Set;

public class FSLegacyContent {
  public String product;
  public String user;
  public Set<FSLegacySession> sessions = null;

  public static FSContent migrate(@NotNull String gson) {
    try {
      FSLegacyContent legacyContent = new GsonBuilder().create().fromJson(gson, FSLegacyContent.class);

      FSContent content = FSContent.create();
      content.product = legacyContent.product;
      content.user = legacyContent.user;

      for (FSLegacySession legacySession : legacyContent.sessions) {
        FSSession session =  FSSession.create(legacySession.id, legacySession.build);
        content.addSession(session);
        for (FSLegacyGroup legacyGroup : legacySession.groups) {
          FSGroup group = FSGroup.create(CollectorGroupDescriptor.create(legacyGroup.id), Collections.emptySet());
          for (Map.Entry<String, String> entry : legacyGroup.metrics.entrySet()) {
            group.getMetrics().add(FSMetric.create(entry.getKey(), Integer.parseInt(entry.getValue())));
          }
          session.addGroup(group);
        }
      }
      return content;
    }
    catch (Exception ignored) {
    }
    return null;
  }
}
