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
package com.intellij.android.designer.model.layout.relative;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.RadViewContainer;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.psi.xml.XmlTag;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Alexander Lobas
 */
public class RadRelativeLayoutComponent extends RadViewContainer {
  @Override
  public void setViewInfo(ViewInfo viewInfo) {
    super.setViewInfo(viewInfo);

    Map<String, RadViewComponent> idToComponent = new HashMap<String, RadViewComponent>();
    for (RadComponent child : getChildren()) {
      RadViewComponent viewChild = (RadViewComponent)child;
      String id = parseIdValue(viewChild.getId());
      if (id != null) {
        idToComponent.put(id, viewChild);
      }
    }

    Map<RadComponent, RelativeInfo> relativeInfos = new HashMap<RadComponent, RelativeInfo>();
    setClientProperty(RelativeInfo.KEY, relativeInfos);

    for (RadComponent child : getChildren()) {
      RelativeInfo info = new RelativeInfo();
      relativeInfos.put(child, info);

      XmlTag tag = ((RadViewComponent)child).getTag();

      info.alignTop = getComponent(idToComponent, tag, "android:layout_alignTop");
      info.alignBottom = getComponent(idToComponent, tag, "android:layout_alignBottom");
      info.alignLeft = getComponent(idToComponent, tag, "android:layout_alignLeft");
      info.alignRight = getComponent(idToComponent, tag, "android:layout_alignRight");
      info.alignBaseline = getComponent(idToComponent, tag, "android:layout_alignBaseline");
      info.above = getComponent(idToComponent, tag, "android:layout_above");
      info.below = getComponent(idToComponent, tag, "android:layout_below");
      info.toLeftOf = getComponent(idToComponent, tag, "android:layout_toLeftOf");
      info.toRightOf = getComponent(idToComponent, tag, "android:layout_toRightOf");

      info.parentTop = "true".equals(tag.getAttributeValue("android:layout_alignParentTop"));
      info.parentBottom = "true".equals(tag.getAttributeValue("android:layout_alignParentBottom"));
      info.parentLeft = "true".equals(tag.getAttributeValue("android:layout_alignParentLeft"));
      info.parentRight = "true".equals(tag.getAttributeValue("android:layout_alignParentRight"));

      info.parentCenterHorizontal = "true".equals(tag.getAttributeValue("android:layout_centerHorizontal"));
      info.parentCenterVertical = "true".equals(tag.getAttributeValue("android:layout_centerVertical"));

      String center = tag.getAttributeValue("android:layout_centerInParent");
      if (!StringUtil.isEmpty(center)) {
        info.parentCenterHorizontal = info.parentCenterVertical = "true".equals(center);
      }
    }
  }

  @Nullable
  private static RadViewComponent getComponent(Map<String, RadViewComponent> idToComponent, XmlTag tag, String attribute) {
    return idToComponent.get(parseIdValue(tag.getAttributeValue(attribute)));
  }

  private static String parseIdValue(String idValue) {
    if (!StringUtil.isEmpty(idValue)) {
      if (idValue.startsWith("@android:id/")) {
        return idValue;
      }
      return idValue.substring(idValue.indexOf('/') + 1);
    }
    return null;
  }
}