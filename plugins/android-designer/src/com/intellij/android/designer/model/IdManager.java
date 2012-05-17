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

import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.text.StringUtil;

import java.util.HashSet;
import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class IdManager {
  public static final String KEY = "IdManager";

  private final Set<String> myIdList = new HashSet<String>();

  public static IdManager get(RadComponent component) {
    return component.getRoot().getClientProperty(KEY);
  }

  public void addComponent(RadViewComponent component) {
    String id = component.getId();
    if (id != null && !id.startsWith("@android:id/")) {
      myIdList.add(id);
    }
  }

  public void removeComponent(RadViewComponent component, boolean withChildren) {
    String id = component.getId();
    if (id != null) {
      myIdList.remove(id);
    }

    if (withChildren) {
      for (RadComponent child : component.getChildren()) {
        removeComponent((RadViewComponent)child, true);
      }
    }
  }

  public String createId(RadViewComponent component) {
    String id = StringUtil.decapitalize(component.getMetaModel().getTag());
    String idValue = "@id/" + id;
    String nextIdValue = idValue;
    int index = 0;

    while (myIdList.contains(nextIdValue)) {
      nextIdValue = idValue + Integer.toString(++index);
    }

    myIdList.add(nextIdValue);
    String newId = "@+id/" + id + (index == 0 ? "" : Integer.toString(index));
    component.getTag().setAttribute("android:id", newId);
    return newId;
  }

  public void ensureIds(final RadViewComponent container) {
    // TODO: rename all references

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        container.accept(new RadComponentVisitor() {
          @Override
          public void endVisit(RadComponent component) {
            RadViewComponent viewComponent = (RadViewComponent)component;
            String idValue = viewComponent.getId();
            if (component == container || (idValue != null && myIdList.contains(idValue))) {
              createId(viewComponent);
            }
          }
        }, true);
      }
    });
  }
}