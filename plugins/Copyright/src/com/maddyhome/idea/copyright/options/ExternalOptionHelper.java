/*
 *  Copyright 2000-2007 JetBrains s.r.o.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.maddyhome.idea.copyright.options;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Comparing;
import com.intellij.openapi.util.JDOMUtil;
import com.maddyhome.idea.copyright.CopyrightProfile;
import org.jdom.Document;
import org.jdom.Element;

import java.io.File;
import java.util.List;

public class ExternalOptionHelper {


  public static boolean loadOptions(File file, CopyrightProfile profile) {
    try {
      Document doc = JDOMUtil.loadDocument(file);
      Element root = doc.getRootElement();
      List list = root.getChildren("component");
      for (Object element : list) {
        Element component = (Element)element;
        String name = component.getAttributeValue("name");
        if (name.equals("CopyrightManager")) {
          final Element child = component.getChild("copyright");
          if (child != null) {
            for (Object o : child.getChildren("option")) {
              if (Comparing.strEqual(((Element)o).getAttributeValue("name"), "myOptions")) {
                final Element valueElement = ((Element)o).getChild("value");
                if (valueElement != null) {
                  extractNoticeAndKeyword(profile, valueElement);
                  return true;
                }
              }
            }
          }
        }
        else if (name.equals("copyright")) {
          extractNoticeAndKeyword(profile, component);
          return true;
        }
      }
    }
    catch (Exception e) {
      logger.error(e);

    }
    return false;
  }

  public static void extractNoticeAndKeyword(CopyrightProfile profile, Element valueElement) {
    for (Object l : valueElement.getChildren()) {
      if (((Element)l).getAttributeValue("name").equals("JAVA")) {
        for (Object o1 : ((Element)l).getChildren("option")) {
          Element opElement = (Element)o1;
          if (opElement.getAttributeValue("name").equals("notice")) {
            profile.setNotice(opElement.getAttributeValue("value"));
          }
          else if (opElement.getAttributeValue("name").equals("keyword")) {
            profile.setKeyword(opElement.getAttributeValue("value"));
          }
        }
        break;
      }
    }
  }


  private ExternalOptionHelper() {
  }

  private static final Logger logger = Logger.getInstance(ExternalOptionHelper.class.getName());
}
