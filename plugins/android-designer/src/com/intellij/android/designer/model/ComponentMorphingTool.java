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

import com.intellij.android.designer.model.morphing.RelativeLayout;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.designer.model.RadLayout;
import com.intellij.openapi.application.ApplicationManager;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Method;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class ComponentMorphingTool {
  protected final RadViewComponent myOldComponent;
  protected final RadViewComponent myNewComponent;

  public ComponentMorphingTool(RadViewComponent oldComponent,
                               RadViewComponent newComponent,
                               MetaModel newModel,
                               @Nullable RadLayout newLayout) throws Exception {
    myOldComponent = oldComponent;
    myNewComponent = newComponent;

    newComponent.setMetaModel(newModel);

    if (newLayout != null) {
      newComponent.setLayout(newLayout);
    }

    if (newComponent != oldComponent) {
      RadComponent parent = oldComponent.getParent();
      newComponent.setParent(parent);

      List<RadComponent> parentChildren = parent.getChildren();
      parentChildren.set(parentChildren.indexOf(oldComponent), newComponent);

      newComponent.setTag(oldComponent.getTag());

      convertChildren();
    }

    ApplicationManager.getApplication().runWriteAction(new Runnable() {
      @Override
      public void run() {
        convertTag();
        myNewComponent.getTag().setName(myNewComponent.getMetaModel().getTag());
      }
    });

    PropertyParser propertyParser = newComponent.getRoot().getClientProperty(PropertyParser.KEY);
    propertyParser.load(newComponent);
    loadChildProperties(propertyParser);
  }

  protected void convertChildren() throws Exception {
    List<RadComponent> oldChildren = myOldComponent.getChildren();
    myNewComponent.getChildren().addAll(oldChildren);

    for (RadComponent childComponent : oldChildren) {
      childComponent.setParent(myNewComponent);
    }
  }

  protected void convertTag() {
  }

  protected void loadChildProperties(PropertyParser propertyParser) throws Exception {
    for (RadComponent childComponent : myNewComponent.getChildren()) {
      propertyParser.load((RadViewComponent)childComponent);
    }
  }

  public RadViewComponent result() {
    return myNewComponent;
  }

  public static RadViewComponent convert(RadViewComponent component, MetaModel target) throws Exception {
    ClassLoader classLoader = ComponentMorphingTool.class.getClassLoader();
    Class<?> sourceConverterClass = classLoader.loadClass(
      "com.intellij.android.designer.model.morphing." + component.getMetaModel().getTag());
    Object sourceConverter = sourceConverterClass.newInstance();

    try {
      Method method = sourceConverterClass.getMethod(target.getTag(), RadViewComponent.class, MetaModel.class);
      return (RadViewComponent)method.invoke(sourceConverter, component, target);
    }
    catch (NoSuchMethodException e) {
      if ("RelativeLayout".equals(target.getTag())) {
        return RelativeLayout.RelativeLayout(component, target);
      }
      throw e;
    }
  }
}