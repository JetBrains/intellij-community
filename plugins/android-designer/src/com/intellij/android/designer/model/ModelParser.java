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

import com.android.ide.common.rendering.api.ViewInfo;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.psi.XmlRecursiveElementVisitor;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;

import javax.swing.*;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ModelParser extends XmlRecursiveElementVisitor {
  private final MetaManager myMetaManager;
  private final XmlFile myXmlFile;
  private RadViewComponent myRootComponent;
  private RadViewComponent myComponent;
  private String myLayoutXmlText;

  public ModelParser(Project project, XmlFile xmlFile) {
    myMetaManager = ViewsMetaManager.getInstance(project);
    myXmlFile = xmlFile;
    parse();
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void parse() {
    myLayoutXmlText = ApplicationManager.getApplication().runReadAction(new Computable<String>() {
      @Override
      public String compute() {
        XmlTag root = myXmlFile.getRootTag();
        if (root != null) {
          root.accept(ModelParser.this);
        }

        return myXmlFile.getText();
      }
    });
  }

  @Override
  public void visitXmlTag(XmlTag tag) {
    try {
      MetaModel metaModel = myMetaManager.getModelByTag(tag.getName());
      if (metaModel == null) {
        metaModel = myMetaManager.getModelByTag("<unknown>");
      }

      RadViewComponent component = createComponent(tag, metaModel);

      if (myRootComponent == null) {
        myRootComponent = component;
      }

      component.setParent(myComponent);
      if (myComponent != null) {
        myComponent.getChildren().add(component);
      }

      myComponent = component;
      super.visitXmlTag(tag);
      myComponent = (RadViewComponent)component.getParent();
    }
    catch (Throwable e) {
      throw new RuntimeException(e);
    }
  }

  private static RadViewComponent createComponent(XmlTag tag, MetaModel metaModel) throws Exception {
    RadViewComponent component = (RadViewComponent)metaModel.getModel().newInstance();
    component.setMetaModel(metaModel);
    component.setTag(tag);

    Class<RadLayout> layout = metaModel.getLayout();
    if (layout != null) {
      component.setLayout(layout.newInstance());
    }

    return component;
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  public RadViewComponent getRootComponent() {
    return myRootComponent;
  }

  public String getLayoutXmlText() {
    return myLayoutXmlText;
  }

  public void updateRootComponent(List<ViewInfo> views, JComponent nativeComponent) throws Exception {
    RadViewComponent rootComponent = myRootComponent;

    if (views.size() == 1) {
      RadViewComponent newRootComponent = createComponent(myXmlFile.getRootTag(), myMetaManager.getModelByTag("<root>"));
      rootComponent.setParent(newRootComponent);
      newRootComponent.getChildren().add(rootComponent);

      updateComponent(rootComponent, views.get(0), nativeComponent, 0, 0);

      newRootComponent.setNativeComponent(nativeComponent);
      newRootComponent.setBounds(0, 0, nativeComponent.getWidth(), nativeComponent.getHeight());

      myRootComponent = newRootComponent;
    }
    else {
      updateRootComponent(rootComponent, views, nativeComponent);
    }
  }

  public static void updateRootComponent(RadViewComponent rootComponent,
                                         List<ViewInfo> views,
                                         JComponent nativeComponent) {
    int size = views.size();
    List<RadComponent> children = rootComponent.getChildren();
    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, 0, 0);
    }

    rootComponent.setNativeComponent(nativeComponent);
    rootComponent.setBounds(0, 0, nativeComponent.getWidth(), nativeComponent.getHeight());
  }

  private static void updateComponent(RadViewComponent component,
                                      ViewInfo view,
                                      JComponent nativeComponent,
                                      int parentX,
                                      int parentY) {
    component.setViewInfo(view);
    component.setNativeComponent(nativeComponent);

    int left = parentX + view.getLeft();
    int top = parentY + view.getTop();
    component.setBounds(left, top, view.getRight() - view.getLeft(), view.getBottom() - view.getTop());

    List<ViewInfo> views = view.getChildren();
    List<RadComponent> children = component.getChildren();
    int size = views.size();

    for (int i = 0; i < size; i++) {
      updateComponent((RadViewComponent)children.get(i), views.get(i), nativeComponent, left, top);
    }
  }
}