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
package com.intellij.android.designer.model.table;

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.android.designer.model.RadViewContainer;
import com.intellij.util.ArrayUtil;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;

/**
 * @author Alexander Lobas
 */
public class RadTableLayoutComponent extends RadViewContainer {
  private int[] myColumnWidths;

  @Override
  public void setViewInfo(ViewInfo viewInfo) {
    super.setViewInfo(viewInfo);
    myColumnWidths = null;
  }

  @Nullable
  public int[] getColumnWidths() {
    if (myColumnWidths == null) {
      try {
        Object viewObject = myViewInfo.getViewObject();
        Class<?> viewClass = viewObject.getClass();
        Field maxWidths = viewClass.getDeclaredField("mMaxWidths");
        maxWidths.setAccessible(true);
        myColumnWidths = (int[])maxWidths.get(viewObject);
      }
      catch (Throwable e) {
      }
      if (myColumnWidths == null) {
        myColumnWidths = ArrayUtil.EMPTY_INT_ARRAY;
      }
    }
    return myColumnWidths;
  }
}