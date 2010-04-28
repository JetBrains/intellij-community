
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
package com.intellij.openapi.application.impl;

import com.intellij.openapi.application.ApplicationNamesInfo;
import com.intellij.openapi.application.PathManager;
import com.intellij.openapi.application.ex.ApplicationInfoEx;
import com.intellij.openapi.components.ApplicationComponent;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.*;
import org.jdom.Document;
import org.jdom.Element;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

public class ApplicationInfoImpl extends ApplicationInfoEx implements JDOMExternalizable, ApplicationComponent {

  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.application.impl.ApplicationInfoImpl");

  @NonNls private static final String BUILD_STUB = "__BUILD_NUMBER__";
  private String myCodeName = null;
  private String myMajorVersion = null;
  private String myMinorVersion = null;
  private String myBuildNumber = null;
  private String myLogoUrl = null;
  private Color myLogoTextColor = new Color(0, 35, 135);  // idea blue
  private String myAboutLogoUrl = null;
  @NonNls private String myIconUrl = "/icon.png";
  @NonNls private String mySmallIconUrl = "/icon_small.png";
  @NonNls private String myOpaqueIconUrl = "/icon.png";
  @NonNls private String myToolWindowIconUrl = "/general/toolWindowProject.png";
  private Calendar myBuildDate = null;
  private String myPackageCode = null;
  private boolean myShowLicensee = true;
  private String myWelcomeScreenCaptionUrl;
  private String myWelcomeScreenDeveloperSloganUrl;
  private UpdateUrls myUpdateUrls;
  private String myDocumentationUrl;
  private String mySupportUrl;
  private String myEAPFeedbackUrl;
  private String myReleaseFeedbackUrl;
  private String myPluginManagerUrl;
  private String myPluginsListUrl;
  private String myPluginsDownloadUrl;
  private String myDefaultUpdateChannel;
  private String myWhatsNewUrl;
  private String myWinKeymapUrl;
  private String myMacKeymapUrl;
  private boolean myEAP;
  @NonNls private String myHelpFileName = "ideahelp.jar";
  @NonNls private String myHelpRootName = "idea";
  @NonNls private String myWebHelpUrl = "http://www.jetbrains.com/idea/webhelp/";
  private List<PluginChooserPage> myPluginChooserPages = new ArrayList<PluginChooserPage>();

  @NonNls private static final String IDEA_PATH = "/idea/";
  @NonNls private static final String ELEMENT_VERSION = "version";
  @NonNls private static final String ATTRIBUTE_MAJOR = "major";
  @NonNls private static final String ATTRIBUTE_MINOR = "minor";
  @NonNls private static final String ATTRIBUTE_CODENAME = "codename";
  @NonNls private static final String ELEMENT_BUILD = "build";
  @NonNls private static final String ATTRIBUTE_NUMBER = "number";
  @NonNls private static final String ATTRIBUTE_DATE = "date";
  @NonNls private static final String ELEMENT_LOGO = "logo";
  @NonNls private static final String ATTRIBUTE_URL = "url";
  @NonNls private static final String ATTRIBUTE_TEXTCOLOR = "textcolor";
  @NonNls private static final String ELEMENT_ABOUT = "about";
  @NonNls private static final String ELEMENT_ICON = "icon";
  @NonNls private static final String ATTRIBUTE_SIZE32 = "size32";
  @NonNls private static final String ATTRIBUTE_SIZE16 = "size16";
  @NonNls private static final String ATTRIBUTE_SIZE12 = "size12";
  @NonNls private static final String ATTRIBUTE_SIZE32OPAQUE = "size32opaque";
  @NonNls private static final String ELEMENT_PACKAGE = "package";
  @NonNls private static final String ATTRIBUTE_CODE = "code";
  @NonNls private static final String ELEMENT_LICENSEE = "licensee";
  @NonNls private static final String ATTRIBUTE_SHOW = "show";
  @NonNls private static final String WELCOME_SCREEN_ELEMENT_NAME = "welcome-screen";
  @NonNls private static final String CAPTION_URL_ATTR = "caption-url";
  @NonNls private static final String SLOGAN_URL_ATTR = "slogan-url";
  @NonNls private static final String UPDATE_URLS_ELEMENT_NAME = "update-urls";
  @NonNls private static final String XML_EXTENSION = ".xml";
  @NonNls private static final String ATTRIBUTE_EAP = "eap";
  @NonNls private static final String HELP_ELEMENT_NAME = "help";
  @NonNls private static final String ATTRIBUTE_HELP_FILE = "file";
  @NonNls private static final String ATTRIBUTE_HELP_ROOT = "root";
  @NonNls private static final String PLUGINS_PAGE_ELEMENT_NAME = "plugins-page";
  @NonNls private static final String ELEMENT_DOCUMENTATION = "documentation";
  @NonNls private static final String ELEMENT_SUPPORT = "support";
  @NonNls private static final String ELEMENT_FEEDBACK = "feedback";
  @NonNls private static final String ATTRIBUTE_RELEASE_URL = "release-url";
  @NonNls private static final String ATTRIBUTE_EAP_URL = "eap-url";
  @NonNls private static final String ELEMENT_PLUGINS = "plugins";
  @NonNls private static final String ATTRIBUTE_LIST_URL = "list-url";
  @NonNls private static final String ATTRIBUTE_DOWNLOAD_URL = "download-url";
  @NonNls private static final String ATTRIBUTE_WEBHELP_URL = "webhelp-url";
  @NonNls private static final String ELEMENT_WHATSNEW = "whatsnew";
  @NonNls private static final String ELEMENT_KEYMAP = "keymap";
  @NonNls private static final String ATTRIBUTE_WINDOWS_URL = "win";
  @NonNls private static final String ATTRIBUTE_MAC_URL = "mac";
  private static final String DEFAULT_PLUGINS_HOST = "http://plugins.intellij.net";

  public void initComponent() { }

  public void disposeComponent() {
  }

  public Calendar getBuildDate() {
    return myBuildDate;
  }

  @Override
  public BuildNumber getBuild() {
    return BuildNumber.fromString(myBuildNumber);
  }

  public String getMajorVersion() {
    return myMajorVersion;
  }

  public String getMinorVersion() {
    return myMinorVersion;
  }

  public String getVersionName() {
    final String fullName = ApplicationNamesInfo.getInstance().getFullProductName();
    if (myEAP) {
      return fullName + " (" + myCodeName + ")";
    }
    return fullName;
  }

  @NonNls
  public String getHelpURL() {
    return "jar:file:///" + getHelpJarPath() + "!/" + myHelpRootName;
  }

  @NonNls
  private String getHelpJarPath() {
    return PathManager.getHomePath() + File.separator + "help" + File.separator + myHelpFileName;
  }

  public String getLogoUrl() {
    return myLogoUrl;
  }

  public Color getLogoTextColor() {
    return myLogoTextColor;
  }

  public String getAboutLogoUrl() {
    return myAboutLogoUrl;
  }

  public String getIconUrl() {
    return myIconUrl;
  }

  public String getSmallIconUrl() {
    return mySmallIconUrl;
  }

  public String getOpaqueIconUrl() {
    return myOpaqueIconUrl;
  }

  public String getToolWindowIconUrl() {
    return myToolWindowIconUrl;
  }

  public String getPackageCode() {
    return myPackageCode;
  }

  public String getWelcomeScreenCaptionUrl() {
    return myWelcomeScreenCaptionUrl;
  }

  public String getWelcomeScreenDeveloperSloganUrl() {
    return myWelcomeScreenDeveloperSloganUrl;
  }

  public boolean isEAP() {
    return myEAP;
  }

  public String getDefaultUpdateChannel() {
    return myDefaultUpdateChannel;
  }

  public UpdateUrls getUpdateUrls() {
    return myUpdateUrls;
  }

  public String getDocumentationUrl() {
    return myDocumentationUrl;
  }

  public String getSupportUrl() {
    return mySupportUrl;
  }

  public String getEAPFeedbackUrl() {
    return myEAPFeedbackUrl;
  }

  public String getReleaseFeedbackUrl() {
    return myReleaseFeedbackUrl;
  }

  @Override
  public String getPluginManagerUrl() {
    return myPluginManagerUrl;
  }

  public String getPluginsListUrl() {
    return myPluginsListUrl;
  }

  public String getPluginsDownloadUrl() {
    return myPluginsDownloadUrl;
  }

  public String getWebHelpUrl() {
    return myWebHelpUrl;
  }

  public String getWhatsNewUrl() {
    return myWhatsNewUrl;
  }

  public String getWinKeymapUrl() {
    return myWinKeymapUrl;
  }

  public String getMacKeymapUrl() {
    return myMacKeymapUrl;
  }

  public String getFullApplicationName() {
    @NonNls StringBuilder buffer = new StringBuilder();
    buffer.append(getVersionName());
    buffer.append(" ");
    if (getMajorVersion() != null && !isEAP()) {
      buffer.append(getMajorVersion());

      if (getMinorVersion() != null && getMinorVersion().length() > 0){
        buffer.append(".");
        buffer.append(getMinorVersion());
      }
    }
    else {
      buffer.append(getBuild().asString());
    }
    return buffer.toString();
  }

  public boolean showLicenseeInfo() {
    return myShowLicensee;
  }

  private static ApplicationInfoImpl ourShadowInstance;

  public static ApplicationInfoEx getShadowInstance() {
    if (ourShadowInstance == null) {
      ourShadowInstance = new ApplicationInfoImpl();
      try {
        Document doc = JDOMUtil.loadDocument(ApplicationInfoImpl.class, IDEA_PATH +
                                                                        ApplicationNamesInfo.getComponentName() +
                                                                        XML_EXTENSION);
        ourShadowInstance.readExternal(doc.getRootElement());
      }
      catch (Exception e) {
        LOG.error(e);
      }
    }
    return ourShadowInstance;
  }

  public void readExternal(Element parentNode) throws InvalidDataException {
    Element versionElement = parentNode.getChild(ELEMENT_VERSION);
    if (versionElement != null) {
      myMajorVersion = versionElement.getAttributeValue(ATTRIBUTE_MAJOR);
      myMinorVersion = versionElement.getAttributeValue(ATTRIBUTE_MINOR);
      myCodeName = versionElement.getAttributeValue(ATTRIBUTE_CODENAME);
      myEAP = Boolean.parseBoolean(versionElement.getAttributeValue(ATTRIBUTE_EAP));
      myDefaultUpdateChannel = versionElement.getAttributeValue("update-channel");
    }

    Element buildElement = parentNode.getChild(ELEMENT_BUILD);
    if (buildElement != null) {
      myBuildNumber = buildElement.getAttributeValue(ATTRIBUTE_NUMBER);
      String dateString = buildElement.getAttributeValue(ATTRIBUTE_DATE);
      if (dateString.equals("__BUILD_DATE__")) {
        myBuildDate = new GregorianCalendar();
      }
      else {
        int year = 0;
        int month = 0;
        int day = 0;
        try {
          year = Integer.parseInt(dateString.substring(0, 4));
          month = Integer.parseInt(dateString.substring(4, 6));
          day = Integer.parseInt(dateString.substring(6, 8));
        }
        catch (Exception ex) {
          //ignore
        }
        if (month > 0) {
          month--;
        }
        myBuildDate = new GregorianCalendar(year, month, day);
      }
    }

    Thread currentThread = Thread.currentThread();
    currentThread.setName(currentThread.getName() + " " + myMajorVersion + "." + myMinorVersion + "#" + myBuildNumber + ", eap:"+myEAP);

    Element logoElement = parentNode.getChild(ELEMENT_LOGO);
    if (logoElement != null) {
      myLogoUrl = logoElement.getAttributeValue(ATTRIBUTE_URL);
      final int rgb = Integer.parseInt(logoElement.getAttributeValue(ATTRIBUTE_TEXTCOLOR), 16);
      myLogoTextColor = new Color(rgb); 
    }

    Element aboutLogoElement = parentNode.getChild(ELEMENT_ABOUT);
    if (aboutLogoElement != null) {
      myAboutLogoUrl = aboutLogoElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element iconElement = parentNode.getChild(ELEMENT_ICON);
    if (iconElement != null) {
      myIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE32);
      mySmallIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE16);
      myOpaqueIconUrl = iconElement.getAttributeValue(ATTRIBUTE_SIZE32OPAQUE);
      final String toolWindowIcon = iconElement.getAttributeValue(ATTRIBUTE_SIZE12);
      if (toolWindowIcon != null) {
        myToolWindowIconUrl = toolWindowIcon;
      }
    }

    Element packageElement = parentNode.getChild(ELEMENT_PACKAGE);
    if (packageElement != null) {
      myPackageCode = packageElement.getAttributeValue(ATTRIBUTE_CODE);
    }

    Element showLicensee = parentNode.getChild(ELEMENT_LICENSEE);
    if (showLicensee != null) {
      myShowLicensee = Boolean.valueOf(showLicensee.getAttributeValue(ATTRIBUTE_SHOW)).booleanValue();
    }

    Element welcomeScreen = parentNode.getChild(WELCOME_SCREEN_ELEMENT_NAME);
    if (welcomeScreen != null) {
      myWelcomeScreenCaptionUrl = welcomeScreen.getAttributeValue(CAPTION_URL_ATTR);
      myWelcomeScreenDeveloperSloganUrl = welcomeScreen.getAttributeValue(SLOGAN_URL_ATTR);
    }

    Element helpElement = parentNode.getChild(HELP_ELEMENT_NAME);
    if (helpElement != null) {
      myHelpFileName = helpElement.getAttributeValue(ATTRIBUTE_HELP_FILE);
      myHelpRootName = helpElement.getAttributeValue(ATTRIBUTE_HELP_ROOT);
      final String webHelpUrl = helpElement.getAttributeValue(ATTRIBUTE_WEBHELP_URL);
      if (webHelpUrl != null) {
        myWebHelpUrl = webHelpUrl;
      }
    }

    Element updateUrls = parentNode.getChild(UPDATE_URLS_ELEMENT_NAME);
    myUpdateUrls = new UpdateUrlsImpl(updateUrls);

    Element documentationElement = parentNode.getChild(ELEMENT_DOCUMENTATION);
    if (documentationElement != null) {
      myDocumentationUrl = documentationElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element supportElement = parentNode.getChild(ELEMENT_SUPPORT);
    if (supportElement != null) {
      mySupportUrl = supportElement.getAttributeValue(ATTRIBUTE_URL);
    }

    Element feedbackElement = parentNode.getChild(ELEMENT_FEEDBACK);
    if (feedbackElement != null) {
      myEAPFeedbackUrl = feedbackElement.getAttributeValue(ATTRIBUTE_EAP_URL);
      myReleaseFeedbackUrl = feedbackElement.getAttributeValue(ATTRIBUTE_RELEASE_URL);
    }

    Element whatsnewElement = parentNode.getChild(ELEMENT_WHATSNEW);
    if (whatsnewElement != null) {
      myWhatsNewUrl = whatsnewElement.getAttributeValue(ATTRIBUTE_URL);
    }

    myPluginsListUrl = DEFAULT_PLUGINS_HOST + "/plugins/list/";
    myPluginsDownloadUrl = DEFAULT_PLUGINS_HOST + "/pluginManager";

    Element pluginsElement = parentNode.getChild(ELEMENT_PLUGINS);
    if (pluginsElement != null) {
      myPluginManagerUrl = pluginsElement.getAttributeValue(ATTRIBUTE_URL);
      final String listUrl = pluginsElement.getAttributeValue(ATTRIBUTE_LIST_URL);
      if (listUrl != null) {
        myPluginsListUrl = listUrl;
      }
      final String downloadUrl = pluginsElement.getAttributeValue(ATTRIBUTE_DOWNLOAD_URL);
      if (downloadUrl != null) {
        myPluginsDownloadUrl = downloadUrl;
      }
    }
    else {
      myPluginManagerUrl = DEFAULT_PLUGINS_HOST;
    }

    final String pluginsHost = System.getProperty("idea.plugins.host");
    if (pluginsHost != null) {
      myPluginsListUrl = myPluginsListUrl.replace(DEFAULT_PLUGINS_HOST, pluginsHost);
      myPluginsDownloadUrl = myPluginsDownloadUrl.replace(DEFAULT_PLUGINS_HOST, pluginsHost);
    }

    Element keymapElement = parentNode.getChild(ELEMENT_KEYMAP);
    if (keymapElement != null) {
      myWinKeymapUrl = keymapElement.getAttributeValue(ATTRIBUTE_WINDOWS_URL);
      myMacKeymapUrl = keymapElement.getAttributeValue(ATTRIBUTE_MAC_URL);
    }

    myPluginChooserPages = new ArrayList<PluginChooserPage>();
    final List children = parentNode.getChildren(PLUGINS_PAGE_ELEMENT_NAME);
    for(Object child: children) {
      myPluginChooserPages.add(new PluginChooserPageImpl((Element) child));
    }
  }

  public List<PluginChooserPage> getPluginChooserPages() {
    return myPluginChooserPages;
  }

  public void writeExternal(Element element) throws WriteExternalException {
    throw new WriteExternalException();
  }


  @NotNull
  public String getComponentName() {
    return ApplicationNamesInfo.getComponentName();
  }

  private static class UpdateUrlsImpl implements UpdateUrls {
    private String myCheckingUrl;
    private String myPatchesUrl;

    private UpdateUrlsImpl(Element element) {
      if (element != null) {
        myCheckingUrl = element.getAttributeValue("check");
        myPatchesUrl = element.getAttributeValue("patches");
      }
    }

    public String getCheckingUrl() {
      return myCheckingUrl;
    }

    public String getPatchesUrl() {
      return myPatchesUrl;
    }
  }

  private static class PluginChooserPageImpl implements PluginChooserPage {
    private final String myTitle;
    private final String myCategory;
    private final String myDependentPlugin;

    private PluginChooserPageImpl(Element e) {
      myTitle = e.getAttributeValue("title");
      myCategory = e.getAttributeValue("category");
      myDependentPlugin = e.getAttributeValue("depends");
    }

    public String getTitle() {
      return myTitle;
    }

    public String getCategory() {
      return myCategory;
    }

    public String getDependentPlugin() {
      return myDependentPlugin;
    }
  }
}
