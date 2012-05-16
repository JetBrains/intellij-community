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
package com.intellij.android.designer.designSurface.layout.relative;

import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ResizeComponentSnapPoint extends ComponentSnapPoint {
  public ResizeComponentSnapPoint(RadViewComponent component, boolean horizontal) {
    super(component, horizontal);
  }

  @Override
  public void execute(final List<RadComponent> components) throws Exception {
    super.execute(components);

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        XmlTag tag = ((RadViewComponent)components.get(0)).getTag();

        if (myBeginSide == Side.top) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentTop");
          ModelParser.deleteAttribute(tag, "android:layout_marginTop");
        }
        else if (myBeginSide == Side.bottom) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentBottom");
          ModelParser.deleteAttribute(tag, "android:layout_marginBottom");
        }
        else if (myBeginSide == Side.left) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentLeft");
          ModelParser.deleteAttribute(tag, "android:layout_marginLeft");
        }
        else if (myBeginSide == Side.right) {
          ModelParser.deleteAttribute(tag, "android:layout_alignParentRight");
          ModelParser.deleteAttribute(tag, "android:layout_marginRight");
        }
      }
    });
  }
}