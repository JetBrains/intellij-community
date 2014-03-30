/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package com.intellij.appengine.facet;

import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.openapi.fileTypes.StdFileTypes;
import icons.GoogleAppEngineIcons;
import org.jetbrains.annotations.NonNls;

/**
 * @author nik
 */
public class AppEngineTemplateGroupDescriptorFactory implements FileTemplateGroupDescriptorFactory {
  @NonNls public static final String APP_ENGINE_WEB_XML_TEMPLATE = "AppEngineWeb.xml";
  @NonNls public static final String APP_ENGINE_JDO_CONFIG_TEMPLATE = "AppEngineJdoConfig.xml";
  @NonNls public static final String APP_ENGINE_JPA_CONFIG_TEMPLATE = "AppEngineJpaConfig.xml";

  public FileTemplateGroupDescriptor getFileTemplatesDescriptor() {
    final FileTemplateDescriptor appEngineXml = new FileTemplateDescriptor(APP_ENGINE_WEB_XML_TEMPLATE, StdFileTypes.XML.getIcon());
    final FileTemplateDescriptor jdoConfigXml = new FileTemplateDescriptor(APP_ENGINE_JDO_CONFIG_TEMPLATE, StdFileTypes.XML.getIcon());
    final FileTemplateDescriptor jpaConfigXml = new FileTemplateDescriptor(APP_ENGINE_JPA_CONFIG_TEMPLATE, StdFileTypes.XML.getIcon());
    return new FileTemplateGroupDescriptor("Google App Engine", GoogleAppEngineIcons.AppEngine, appEngineXml, jdoConfigXml, jpaConfigXml);
  }
}
