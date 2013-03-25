/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.android.designer.model;

import com.intellij.designer.model.MetaManager;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.project.Project;
import com.intellij.util.Alarm;
import com.intellij.util.ui.update.MergingUpdateQueue;

/**
 * @author Alexander Lobas
 */
public class ViewsMetaManager extends MetaManager {
  private final MergingUpdateQueue mySessionQueue;

  public ViewsMetaManager(Project project) {
    super(project, "views-meta-model.xml");
    mySessionQueue = new MergingUpdateQueue("android.designer", 300, true, null, project, null, Alarm.ThreadToUse.OWN_THREAD);
  }

  public MergingUpdateQueue getSessionQueue() {
    return mySessionQueue;
  }

  public static ViewsMetaManager getInstance(Project project) {
    return ServiceManager.getService(project, ViewsMetaManager.class);
  }
}