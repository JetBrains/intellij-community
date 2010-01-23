/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

/*
 * Created by IntelliJ IDEA.
 * User: mike
 * Date: Sep 16, 2002
 * Time: 5:17:44 PM
 * To change template for new class use 
 * Code Style | Class Templates options (Tools | IDE Options).
 */
package com.intellij.openapi.application.ex;

import com.intellij.openapi.application.ApplicationInfo;

import java.awt.*;
import java.util.List;

public abstract class ApplicationInfoEx extends ApplicationInfo {

  public static ApplicationInfoEx getInstanceEx() {
    return (ApplicationInfoEx) getInstance();
  }

  public abstract String getLogoUrl();

  public abstract Color getLogoTextColor();

  public abstract String getAboutLogoUrl();

  public abstract String getIconUrl();

  public abstract String getSmallIconUrl();

  public abstract String getOpaqueIconUrl();

  public abstract String getToolWindowIconUrl();

  public abstract String getPackageCode();

  public abstract String getFullApplicationName();

  public abstract boolean showLicenseeInfo();


  public abstract String getWelcomeScreenCaptionUrl();

  public abstract String getWelcomeScreenDeveloperSloganUrl();

  public abstract boolean isEAP();


  public abstract UpdateUrls getUpdateUrls();

  public abstract String getDocumentationUrl();

  public abstract String getSupportUrl();

  public abstract String getEAPFeedbackUrl();

  public abstract String getReleaseFeedbackUrl();

  public abstract String getPluginManagerUrl();

  public abstract String getPluginsListUrl();

  public abstract String getPluginsDownloadUrl();

  public abstract String getWebHelpUrl();

  public abstract String getWhatsNewUrl();

  public abstract String getWinKeymapUrl();

  public abstract String getMacKeymapUrl();

  public interface UpdateUrls {
    String getCheckingUrl();
    String getPatchesUrl();
  }

  public interface PluginChooserPage {
    String getTitle();
    String getCategory();
    String getDependentPlugin();
  }

  public abstract List<PluginChooserPage> getPluginChooserPages();
}
