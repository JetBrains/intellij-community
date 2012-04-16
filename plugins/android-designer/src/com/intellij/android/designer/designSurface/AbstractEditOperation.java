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
package com.intellij.android.designer.designSurface;

import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * @author Alexander Lobas
 */
public abstract class AbstractEditOperation extends com.intellij.designer.designSurface.AbstractEditOperation {
  public AbstractEditOperation(RadComponent container, OperationContext context) {
    super(container, context);
  }

  @Override
  public void execute() throws Exception {
    execute(null);
  }

  protected void execute(@Nullable RadViewComponent insertBefore) throws Exception {
    execute(myContext, (RadViewComponent)myContainer, myComponents, insertBefore);
  }

  public static void execute(OperationContext context,
                             RadViewComponent container,
                             List<RadComponent> components,
                             @Nullable RadViewComponent insertBefore) throws Exception {
    if (context.isAdd() || context.isMove()) {
      for (RadComponent component : components) {
        ModelParser.moveComponent(container, (RadViewComponent)component, insertBefore);
      }
    }
    else if (context.isCreate()) {
      for (RadComponent component : components) {
        ModelParser.addComponent(container, (RadViewComponent)component, insertBefore);
      }
    }
    else if (context.isPaste()) {
      for (RadComponent component : components) {
        ModelParser.pasteComponent(container, (RadViewComponent)component, insertBefore);
      }
    }
  }
}