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

import com.android.sdklib.IAndroidTarget;
import com.intellij.android.designer.model.ModelParser;
import com.intellij.android.designer.model.RadViewComponent;
import com.intellij.android.designer.model.ViewsMetaManager;
import com.intellij.designer.designSurface.tools.ComponentPasteFactory;
import com.intellij.designer.model.MetaManager;
import com.intellij.designer.model.MetaModel;
import com.intellij.designer.model.RadComponent;
import com.intellij.openapi.module.Module;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jetbrains.android.uipreview.ProjectClassLoader;
import org.jetbrains.annotations.NotNull;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Alexander Lobas
 */
public class AndroidPasteFactory implements ComponentPasteFactory {
  public static final String KEY = "PASTE_DATA";

  private final Module myModule;
  private final IAndroidTarget myTarget;
  private final MetaManager myMetaManager;
  private final String myXmlComponents;
  private ClassLoader myClassLoader;

  public AndroidPasteFactory(Module module, IAndroidTarget target, String xmlComponents) {
    myModule = module;
    myTarget = target;
    myMetaManager = ViewsMetaManager.getInstance(module.getProject());
    myXmlComponents = xmlComponents;
  }

  @NotNull
  @Override
  public List<RadComponent> create() throws Exception {
    myClassLoader = ProjectClassLoader.create(myTarget, myModule);

    List<RadComponent> components = new ArrayList<RadComponent>();

    Document document = new SAXBuilder().build(new StringReader(myXmlComponents), "UTF-8");
    for (Object element : document.getRootElement().getChildren("component")) {
      components.add(createComponent((Element)element));
    }

    return components;
  }

  private RadComponent createComponent(Element element) throws Exception {
    MetaModel metaModel = myMetaManager.getModelByTag(element.getAttributeValue("tag"));

    String target = metaModel.getTarget();
    if (target != null) {
      myClassLoader.loadClass(target);
    }

    RadViewComponent component = ModelParser.createComponent(null, metaModel);
    component.setClientProperty(KEY, element.getChild("properties"));

    for (Object childElement : element.getChildren("component")) {
      component.add(createComponent((Element)childElement), null);
    }

    return component;
  }
}