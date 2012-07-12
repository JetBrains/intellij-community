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
package com.intellij.android.designer.model.layout.table;

import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.grid.RadCaptionRow;
import com.intellij.designer.designSurface.EditableArea;
import com.intellij.designer.model.IGroupDeleteComponent;
import com.intellij.designer.model.RadComponent;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadCaptionTableRow extends RadCaptionRow<RadViewComponent> implements IGroupDeleteComponent {
  public RadCaptionTableRow(EditableArea mainArea, RadViewComponent component) {
    super(mainArea, component, -1, 0, component.getBounds().height, !RadTableRowLayout.is(component));
  }

  public RadViewComponent getComponent() {
    return myContainer;
  }

  @Override
  public void delete(List<RadComponent> rows) throws Exception {
    List<RadComponent> deletedComponents = new ArrayList<RadComponent>();

    for (RadComponent component : rows) {
      RadCaptionTableRow row = (RadCaptionTableRow)component;
      row.myContainer.delete();
      deletedComponents.add(row.myContainer);
    }

    deselect(deletedComponents);
  }
}