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
package com.intellij.android.designer.designSurface.layout.actions;

import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.selection.ResizeSelectionDecorator;
import com.intellij.designer.model.RadComponent;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RelativeLayoutResizeOperation implements EditOperation {
  public static final String TYPE = "relative_resize";

  public RelativeLayoutResizeOperation(OperationContext context) {
  }

  @Override
  public void setComponent(RadComponent component) {
    // TODO: Auto-generated method stub
  }

  @Override
  public void setComponents(List<RadComponent> components) {
    // TODO: Auto-generated method stub
  }

  @Override
  public void showFeedback() {
    // TODO: Auto-generated method stub
  }

  @Override
  public void eraseFeedback() {
    // TODO: Auto-generated method stub
  }

  @Override
  public boolean canExecute() {
    return false;  // TODO: Auto-generated method stub
  }

  @Override
  public void execute() throws Exception {
    // TODO: Auto-generated method stub
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  // ResizePoint
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public static void points(ResizeSelectionDecorator decorator) {
    // XXX
  }
}