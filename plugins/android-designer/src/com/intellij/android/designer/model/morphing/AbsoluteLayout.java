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
package com.intellij.android.designer.model.morphing;

import com.android.SdkConstants;
import com.intellij.android.designer.model.ComponentMorphingTool;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.relative.RadRelativeLayout;
import com.intellij.android.designer.model.layout.relative.RadRelativeLayoutComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.psi.xml.XmlAttribute;
import com.intellij.psi.xml.XmlTag;

/**
 * @author Alexander Lobas
 */
public class AbsoluteLayout {
  public static RadViewComponent RelativeLayout(RadViewComponent component, MetaModel target) throws Exception {
    return new ComponentMorphingTool(component, new RadRelativeLayoutComponent(), target, new RadRelativeLayout()) {
      @Override
      protected void convertTag() {
        for (RadComponent childComponent : myNewComponent.getChildren()) {
          XmlTag tag = ((RadViewComponent)childComponent).getTag();

          XmlAttribute xAttribute = tag.getAttribute("layout_x", SdkConstants.NS_RESOURCES);
          if (xAttribute != null) {
            tag.setAttribute("layout_alignParentLeft", SdkConstants.NS_RESOURCES, "true");
            xAttribute.setName(xAttribute.getNamespacePrefix() + ":layout_marginLeft");
          }

          XmlAttribute yAttribute = tag.getAttribute("layout_y", SdkConstants.NS_RESOURCES);
          if (yAttribute != null) {
            tag.setAttribute("layout_alignParentTop", SdkConstants.NS_RESOURCES, "true");
            yAttribute.setName(yAttribute.getNamespacePrefix() + ":layout_marginTop");
          }
        }
      }
    }.result();
  }
}