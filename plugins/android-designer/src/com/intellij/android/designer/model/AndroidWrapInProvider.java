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

import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.WrapInProvider;
import com.intellij.openapi.project.Project;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public final class AndroidWrapInProvider extends WrapInProvider<RadViewComponent> {
  public AndroidWrapInProvider(Project project) {
    super(ViewsMetaManager.getInstance(project));
  }

  @Override
  public RadComponent wrapIn(RadViewComponent parent, List<RadViewComponent> components, MetaModel target) throws Exception {
    RadViewComponent newParent = ModelParser.createComponent(null, target);

    ModelParser.addComponent(parent, newParent, components.get(0));

    for (RadViewComponent component : components) {
      ModelParser.moveComponent(newParent, component, null);
    }

    RadViewLayout layout = (RadViewLayout)parent.getLayout();
    layout.wrapIn(newParent, components);

    return newParent;
  }
}