// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Alexander Lobas
 */
@Deprecated(forRemoval = true)
public final class NotificationGroupBean {
  @Attribute("parentId")
  public String parentId;

  @Attribute("groupId")
  public String groupId;

  @Attribute("replaceTitle")
  public String replaceTitle;

  @Attribute("shortTitle")
  public String shortTitle;
}