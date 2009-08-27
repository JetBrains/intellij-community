package com.intellij.appengine.facet;

import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptorFactory;
import com.intellij.ide.fileTemplates.FileTemplateGroupDescriptor;
import com.intellij.ide.fileTemplates.FileTemplateDescriptor;
import com.intellij.appengine.util.AppEngineUtil;
import com.intellij.openapi.fileTypes.StdFileTypes;
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
    return new FileTemplateGroupDescriptor("Google App Engine", AppEngineUtil.APP_ENGINE_ICON, appEngineXml, jdoConfigXml, jpaConfigXml);
  }
}
