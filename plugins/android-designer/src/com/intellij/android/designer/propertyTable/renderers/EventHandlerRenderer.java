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
package com.intellij.android.designer.propertyTable.renderers;

import com.intellij.designer.model.RadComponent;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.android.dom.attrs.AttributeFormat;

import java.util.Set;

/**
 * @author Alexander Lobas
 */
public class EventHandlerRenderer extends ResourceRenderer {
  public EventHandlerRenderer(Set<AttributeFormat> formats) {
    super(formats);
  }

  @Override
  protected void formatValue(RadComponent component, String value) {
    super.formatValue(component, value);
    if (!StringUtil.isEmpty(value)) {
      myColoredComponent.setIcon(AllIcons.Nodes.Method);
    }
  }
}