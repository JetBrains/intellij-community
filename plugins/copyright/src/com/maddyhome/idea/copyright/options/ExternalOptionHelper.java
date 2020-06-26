// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.maddyhome.idea.copyright.options;

import com.intellij.copyright.CopyrightBundle;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.JDOMUtil;
import com.intellij.openapi.util.text.StringUtil;
import com.maddyhome.idea.copyright.CopyrightProfile;
import org.jdom.Element;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

public final class ExternalOptionHelper {


  @Nullable
  public static List<CopyrightProfile> loadOptions(File file) {
    try {
      List<CopyrightProfile> profiles = new ArrayList<>();
      Element root = JDOMUtil.load(file);
      if (root.getName().equals("component")) {
        final Element copyrightElement = root.getChild("copyright");
        if (copyrightElement != null) extractNewNoticeAndKeyword(copyrightElement, profiles);
      }
      else {
        for (Element component : root.getChildren("component")) {
          String name = component.getAttributeValue("name");
          if (name.equals("CopyrightManager")) {
            for (Object o : component.getChildren("copyright")) {
              extractNewNoticeAndKeyword((Element)o, profiles);
            }
          }
          else if (name.equals("copyright")) {
            extractNoticeAndKeyword(component, profiles);
          }
        }
      }
      return profiles;
    }
    catch (Exception e) {
      logger.info(e);
      Messages.showErrorDialog(e.getMessage(), CopyrightBundle.message("dialog.title.import.failure"));
      return null;
    }
  }

  public static void extractNoticeAndKeyword(Element valueElement, List<? super CopyrightProfile> profiles) {
    CopyrightProfile profile = new CopyrightProfile();
    boolean extract = false;
    for (Object l : valueElement.getChildren("LanguageOptions")) {
      if (((Element)l).getAttributeValue("name").equals("__TEMPLATE__")) {
        for (Object o1 : ((Element)l).getChildren("option")) {
          extract |= extract(profile, (Element)o1);
        }
        break;
      }
    }
    if (extract) profiles.add(profile);
  }

  public static void extractNewNoticeAndKeyword(Element valueElement, List<? super CopyrightProfile> profiles) {
    CopyrightProfile profile = new CopyrightProfile();
    boolean extract = false;
    for (Object l : valueElement.getChildren("option")) {
      extract |= extract(profile, (Element)l);
    }
    if (extract) profiles.add(profile);
  }

  private static boolean extract(final CopyrightProfile profile, final Element el) {
    if (el.getAttributeValue("name").equals("notice")) {
      profile.setNotice(el.getAttributeValue("value"));
      return true;
    }
    else if (el.getAttributeValue("name").equals("keyword")) {
      profile.setKeyword(el.getAttributeValue("value"));
    } else if (el.getAttributeValue("name").equals("myName")) {
      profile.setName(el.getAttributeValue("value"));
    }
    else if (el.getAttributeValue("name").equals("allowReplaceKeyword")) {
      profile.setAllowReplaceRegexp(StringUtil.escapeToRegexp(el.getAttributeValue("value")));
    }
    else if (el.getAttributeValue("name").equals("allowReplaceRegexp")) {
      profile.setAllowReplaceRegexp(el.getAttributeValue("value"));
    }
    return false;
  }


  private ExternalOptionHelper() {
  }

  private static final Logger logger = Logger.getInstance(ExternalOptionHelper.class.getName());
}
