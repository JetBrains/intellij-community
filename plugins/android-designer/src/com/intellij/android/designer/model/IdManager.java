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

import com.android.SdkConstants;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadComponentVisitor;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.util.Pair;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

  private static String parseIdValue(String idValue) {
    if (idValue != null && !idValue.startsWith("@android:id/")) {
      return idValue.substring(idValue.indexOf('/') + 1);
    }
    return null;
  }

  public void addComponent(RadViewComponent component) {
    String idValue = parseIdValue(component.getId());
    if (idValue != null) {
      myIdList.add(idValue);
    }
  }

  public void removeComponent(RadViewComponent component, boolean withChildren) {
    String idValue = parseIdValue(component.getId());
    if (idValue != null) {
      myIdList.remove(idValue);
    }

    if (withChildren) {
      for (RadComponent child : component.getChildren()) {
        removeComponent((RadViewComponent)child, true);
      }
    }
  }

  public String createId(RadViewComponent component) {
    String idValue = StringUtil.decapitalize(component.getMetaModel().getTag());
    String nextIdValue = idValue;
    int index = 0;

    while (myIdList.contains(nextIdValue)) {
      nextIdValue = idValue + Integer.toString(++index);
    }

    myIdList.add(nextIdValue);
    String newId = "@+id/" + idValue + (index == 0 ? "" : Integer.toString(index));
    component.getTag().setAttribute("id", SdkConstants.NS_RESOURCES, newId);
    return newId;
  }

  public void ensureIds(final RadViewComponent container) {
    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        final List<Pair<Pair<String, String>, String>> replaceList = new ArrayList<Pair<Pair<String, String>, String>>();

        container.accept(new RadComponentVisitor() {
          @Override
          public void endVisit(RadComponent component) {
            RadViewComponent viewComponent = (RadViewComponent)component;
            String idValue = parseIdValue(viewComponent.getId());
            if (component == container) {
              createId(viewComponent);
            }
            else if (idValue != null && myIdList.contains(idValue)) {
              createId(viewComponent);
              replaceList.add(new Pair<Pair<String, String>, String>(
                new Pair<String, String>("@id/" + idValue, "@+id/" + idValue),
                viewComponent.getId()));
            }
            else {
              addComponent(viewComponent);
            }
          }
        }, true);

        if (!replaceList.isEmpty()) {
          replaceIds(container, replaceList);
        }
      }
    });
  }

  public static void replaceIds(RadViewComponent container, final List<Pair<Pair<String, String>, String>> replaceList) {
    container.accept(new RadComponentVisitor() {
      @Override
      public void endVisit(RadComponent component) {
        XmlTag tag = ((RadViewComponent)component).getTag();
        for (XmlAttribute attribute : tag.getAttributes()) {
          String value = attribute.getValue();

          for (Pair<Pair<String, String>, String> replace : replaceList) {
            if (replace.first.first.equals(value) || replace.first.second.equals(value)) {
              attribute.setValue(replace.second);
              break;
            }
          }
        }
      }
    }, true);
  }
}