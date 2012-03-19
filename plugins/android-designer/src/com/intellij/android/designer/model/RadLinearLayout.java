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
package com.intellij.android.designer.model;

import com.intellij.designer.componentTree.TreeEditOperation;
import com.intellij.designer.designSurface.EditOperation;
import com.intellij.designer.designSurface.FeedbackLayer;
import com.intellij.designer.designSurface.OperationContext;
import com.intellij.designer.designSurface.feedbacks.AlphaComponent;
import com.intellij.designer.model.RadComponent;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class RadLinearLayout extends RadViewLayout {
  @Override
  public EditOperation processChildOperation(OperationContext context) {
    if (context.isCreate()) {
      return context.isTree() ? new TreeCreateOperation(myContainer, context) : new CreateOperation(context);
    }
    return null;
  }

  private void addNewComponent(RadViewComponent newComponent, @Nullable RadViewComponent insertBefore) throws Exception {
    RadViewComponent container = (RadViewComponent)myContainer;
    newComponent.setParent(container);

    List<RadComponent> children = container.getChildren();
    if (insertBefore == null) {
      children.add(newComponent);
    }
    else {
      children.add(children.indexOf(insertBefore), newComponent);
    }

    ModelParser
      .setComponentTag(container.getTag(), newComponent, insertBefore == null ? null : insertBefore.getTag());

    PropertyParser propertyParser = container.getRoot().getClientProperty(PropertyParser.KEY);
    propertyParser.load(newComponent);
  }

  private class TreeCreateOperation extends TreeEditOperation {
    public TreeCreateOperation(RadComponent host, OperationContext context) {
      super(host, context);
    }

    @Override
    protected void execute(RadComponent insertBefore) throws Exception {
      addNewComponent((RadViewComponent)myComponents.get(0), (RadViewComponent)insertBefore);
    }
  }

  private class CreateOperation implements EditOperation {
    private final OperationContext myContext;
    private RadComponent myComponent;
    private JComponent myFeedback;

    private CreateOperation(OperationContext context) {
      myContext = context;
    }

    @Override
    public void setComponent(RadComponent component) {
      myComponent = component;
    }

    @Override
    public void setComponents(List<RadComponent> components) {
    }

    @Override
    public void showFeedback() {
      FeedbackLayer layer = myContext.getArea().getFeedbackLayer();

      if (myFeedback == null) {
        myFeedback = new AlphaComponent(Color.green);
        layer.add(myFeedback);
        myFeedback.setBounds(myContainer.getBounds(layer));
        layer.repaint();
      }
    }

    @Override
    public void eraseFeedback() {
      if (myFeedback != null) {
        FeedbackLayer layer = myContext.getArea().getFeedbackLayer();
        layer.remove(myFeedback);
        layer.repaint();
        myFeedback = null;
      }
    }

    @Override
    public boolean canExecute() {
      return true;
    }

    @Override
    public void execute() throws Exception {
      addNewComponent((RadViewComponent)myComponent, null);
    }
  }
}