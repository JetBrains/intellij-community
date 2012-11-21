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
package com.intellij.android.designer.model.layout;

import com.android.SdkConstants;
import com.intellij.android.designer.model.RadViewComponent;

/**
 * @author Alexander Lobas
 */
public class RadRadioGroupLayout extends RadLinearLayout {
  @Override
  public boolean isHorizontal() {
    return "horizontal".equals(((RadViewComponent)myContainer).getTag().getAttributeValue("orientation", SdkConstants.NS_RESOURCES));
  }
}