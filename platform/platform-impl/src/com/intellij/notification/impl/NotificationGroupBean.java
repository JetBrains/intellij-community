// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.notification.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Alexander Lobas
 */
@Deprecated
public class NotificationGroupBean extends AbstractExtensionPointBean {
  @Attribute("parentId")
  public String parentId;

  @Attribute("groupId")
  public String groupId;

  @Attribute("replaceTitle")
  public String replaceTitle;

  @Attribute("shortTitle")
  public String shortTitle;
}