/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.notification.impl;

import com.intellij.openapi.extensions.AbstractExtensionPointBean;
import com.intellij.util.xmlb.annotations.Attribute;

/**
 * @author Alexander Lobas
 */
public class NotificationParentGroupBean extends AbstractExtensionPointBean {
  @Attribute("id")
  public String id;

  @Attribute("parentId")
  public String parentId;

  @Attribute("title")
  public String title;

  @Attribute("titlePrefix")
  public String titlePrefix;

  @Override
  public String toString() {
    return title;
  }
}