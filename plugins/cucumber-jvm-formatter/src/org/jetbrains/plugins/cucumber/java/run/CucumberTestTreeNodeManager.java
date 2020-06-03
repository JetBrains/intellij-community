// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.cucumber.java.run;

import com.intellij.junit4.JUnitTestTreeNodeManager;
import com.intellij.rt.execution.junit.MapSerializerUtil;
import org.junit.runner.Description;

import java.io.File;
import java.lang.reflect.Field;
import java.net.URL;

@SuppressWarnings({"override", "unused"})
public class CucumberTestTreeNodeManager implements JUnitTestTreeNodeManager {
  private static final String CLASSPATH_PREFIX = "classpath:";
  private static final String FILE_COLON_PREFIX = "file:";
  private static final String FILE_URL_PREFIX = "file://";

  @Override
  public JUnitTestTreeNodeManager.TestNodePresentation getRootNodePresentation(String fullName) {
    return new JUnitTestTreeNodeManager.TestNodePresentation(fullName, null);
  }

  @Override
  public String getNodeName(String fqName, boolean splitBySlash) {
    return fqName;
  }

  @Override
  public String getTestLocation(Description description, String className, String methodName) {
    try {
      Field descriptionField = Description.class.getDeclaredField("fUniqueId");
      descriptionField.setAccessible(true);
      String elementUrl = descriptionField.get(description).toString();
      String elementSMUrl = elementUrl;
      if (elementUrl.startsWith(CLASSPATH_PREFIX)) {
        elementUrl = elementUrl.substring(CLASSPATH_PREFIX.length());
        int colonIndex = elementUrl.lastIndexOf(":");
        if (colonIndex < 0) {
          return "";
        }

        int lineNumber = Integer.parseInt(elementUrl.substring(colonIndex + 1));
        elementUrl = elementUrl.substring(0, colonIndex);
        URL url = this.getClass().getResource("/" + elementUrl);
        if (url == null) {
          return "";
        }
        elementSMUrl = FILE_URL_PREFIX + url.getFile() + ":" + lineNumber;
      } else if (elementUrl.startsWith(FILE_COLON_PREFIX)){
        elementUrl = elementUrl.substring(FILE_COLON_PREFIX.length());
        elementUrl = System.getProperty("user.dir") + File.separator + elementUrl;
        elementSMUrl = FILE_URL_PREFIX + elementUrl;
      }

      return "locationHint='" + MapSerializerUtil.escapeStr(elementSMUrl, MapSerializerUtil.STD_ESCAPER) + "'";
    } catch (NoSuchFieldException ignored) {
    } catch (IllegalAccessException ignored) {
    }
    return "";
  }
}
