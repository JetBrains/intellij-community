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
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.layout.RadLinearLayout;
import com.intellij.android.designer.model.layout.RadRadioGroupLayout;
import com.intellij.android.designer.model.layout.grid.RadGridLayout;
import com.intellij.android.designer.model.layout.grid.RadGridLayoutComponent;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadLayout;

/**
 * @author Alexander Lobas
 */
public class RadioGroup {
  public static RadViewComponent LinearLayout(RadViewComponent component, MetaModel target) throws Exception {
    return convert(component, component, target, new RadLinearLayout());
  }

  public static RadViewComponent GridLayout(RadViewComponent component, MetaModel target) throws Exception {
    return convert(component, new RadGridLayoutComponent(), target, new RadGridLayout());
  }

  private static RadViewComponent convert(RadViewComponent oldComponent, RadViewComponent newComponent, MetaModel target, RadLayout layout)
    throws Exception {
    final boolean horizontal = ((RadRadioGroupLayout)oldComponent.getLayout()).isHorizontal();

    return new ComponentMorphingTool(oldComponent, newComponent, target, layout) {
      @Override
      protected void convertTag() {
        if (horizontal) {
          ModelParser.deleteAttribute(myNewComponent, "orientation");
        }
        else {
          myNewComponent.getTag().setAttribute("orientation", SdkConstants.NS_RESOURCES, "vertical");
        }
      }
    }.result();
  }

  public static RadViewComponent TableLayout(RadViewComponent component, MetaModel target) throws Exception {
    boolean horizontal = ((RadRadioGroupLayout)component.getLayout()).isHorizontal();
    return LinearLayout.TableLayout(component, target, horizontal);
  }
}