/*
 * Copyright (c) 2000-2006 JetBrains s.r.o. All Rights Reserved.
 */

package com.intellij.lang.ant;

import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlTag;
import com.intellij.psi.xml.XmlFileNSInfoProvider;
import com.intellij.openapi.components.ProjectComponent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NonNls;

/**
 * Created by IntelliJ IDEA.
 * User: Maxim.Mossienko
 * Date: Apr 11, 2006
 * Time: 5:34:27 PM
 * To change this template use File | Settings | File Templates.
 */
public class AntDefaultNSProvider implements XmlFileNSInfoProvider, ProjectComponent {
  private static final @NonNls String ANT_URI = "http://ant.apache.org/schema.xsd"; // XmlUtil.ANT_URI

  // nsPrefix, namespaceId
  public String[][] getDefaultNamespaces(@NotNull XmlFile file) {
    if (file.getCopyableUserData(XmlFile.ANT_BUILD_FILE) != null)
      return new String[][]{new String[]{"", ANT_URI}};

    final XmlTag tag = file.getDocument().getRootTag();

    if ("project".equals(tag.getName()) && tag.getContext() instanceof XmlDocument) {
      if (tag.getAttributeValue("default") != null) {
        return new String[][]{new String[]{"", ANT_URI}};
      }
    }
    return null;
  }

  public void projectOpened() {}
  public void projectClosed() {}

  @NonNls
  public String getComponentName() {
    return "AntSupport.DefaultNSInfoProvider";
  }

  public void initComponent() {}

  public void disposeComponent() {}
}
