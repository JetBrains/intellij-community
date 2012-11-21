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
package com.intellij.android.designer.profile;

import com.android.SdkConstants;
import com.android.resources.ResourceType;
import com.android.resources.ScreenSize;
import com.android.sdklib.IAndroidTarget;
import com.android.sdklib.devices.State;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.containers.hash.HashSet;
import org.jetbrains.android.dom.manifest.Activity;
import org.jetbrains.android.dom.manifest.Application;
import org.jetbrains.android.dom.manifest.Manifest;
import org.jetbrains.android.dom.resources.ResourceElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.android.dom.resources.Style;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidPlatform;
import org.jetbrains.android.sdk.AndroidTargetData;
import org.jetbrains.android.uipreview.ThemeData;
import org.jetbrains.android.util.AndroidUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

/**
 * @author Eugene.Kudelevsky
 * @author Alexander Lobas
 */
public class ThemeManager {
  public final static ThemeData FRAMEWORK = new ThemeData("Framework themes", false);
  public final static ThemeData PROJECT = new ThemeData("Project themes", true);

  private final List<ThemeData> myThemes = new ArrayList<ThemeData>();
  private final Set<ThemeData> myAddedThemes = new HashSet<ThemeData>();
  private final ProfileManager myProfile;

  public ThemeManager(ProfileManager profile) {
    myProfile = profile;
  }

  public List<ThemeData> getThemes() {
    return myThemes;
  }

  public Set<ThemeData> getAddedThemes() {
    return myAddedThemes;
  }

  public void loadThemes(final Runnable done) {
    final AndroidFacet facet = AndroidFacet.getInstance(myProfile.getModule());
    if (facet == null) {
      done.run();
      return;
    }

    List<ThemeData> projectThemes = new ArrayList<ThemeData>();
    collectThemesFromManifest(facet, projectThemes, true);
    collectProjectThemes(facet, projectThemes);

    if (projectThemes.size() > 0) {
      myThemes.add(PROJECT);
      myThemes.addAll(projectThemes);
    }

    AndroidTargetData targetData = null;
    AndroidPlatform androidPlatform = AndroidPlatform.getInstance(myProfile.getModule());
    if (androidPlatform != null) {
      IAndroidTarget target = myProfile.getSelectedTarget();
      if (target == null) {
        target = androidPlatform.getTarget();
      }
      targetData = androidPlatform.getSdkData().getTargetData(target);
    }

    if (targetData == null || targetData.areThemesCached()) {
      collectFrameworkThemes(facet, targetData);
      done.run();
    }
    else {
      final AndroidTargetData finalTargetData = targetData;
      ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
        @Override
        public void run() {
          collectFrameworkThemes(facet, finalTargetData);

          ApplicationManager.getApplication().invokeLater(new Runnable() {
            @Override
            public void run() {
              done.run();
            }
          });
        }
      });
    }
  }

  //////////////////////////////////////////////////////////////////////////////////////////
  //
  //
  //
  //////////////////////////////////////////////////////////////////////////////////////////

  private void collectThemesFromManifest(final AndroidFacet facet,
                                         final List<ThemeData> resultList,
                                         final boolean fromProject) {
    ApplicationManager.getApplication().runReadAction(new Runnable() {
      @Override
      public void run() {
        doCollectThemesFromManifest(facet, resultList, fromProject);
      }
    });
  }

  private void doCollectThemesFromManifest(AndroidFacet facet,
                                           List<ThemeData> resultList,
                                           boolean fromProject) {
    Manifest manifest = facet.getManifest();
    if (manifest == null) {
      return;
    }

    Application application = manifest.getApplication();
    if (application == null) {
      return;
    }

    List<ThemeData> activityThemesList = new ArrayList<ThemeData>();

    ThemeData preferredTheme = null;
    XmlTag applicationTag = application.getXmlTag();
    if (applicationTag != null) {
      String applicationThemeRef = applicationTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
      if (applicationThemeRef != null) {
        preferredTheme = getThemeByRef(applicationThemeRef);
      }
    }

    if (preferredTheme == null) {
      AndroidPlatform platform = AndroidPlatform.getInstance(myProfile.getModule());
      IAndroidTarget target = platform != null ? platform.getTarget() : null;
      IAndroidTarget renderingTarget = myProfile.getSelectedTarget();
      State configuration = myProfile.getSelectedDeviceConfiguration();

      ScreenSize screenSize = configuration.getHardware().getScreen().getSize();
      preferredTheme = getThemeByRef(getDefaultTheme(target, renderingTarget, screenSize));
    }

    if (!myAddedThemes.contains(preferredTheme) && fromProject == preferredTheme.isProjectTheme()) {
      myAddedThemes.add(preferredTheme);
      resultList.add(preferredTheme);
    }

    for (Activity activity : application.getActivities()) {
      XmlTag activityTag = activity.getXmlTag();
      if (activityTag != null) {
        String activityThemeRef = activityTag.getAttributeValue("theme", SdkConstants.NS_RESOURCES);
        if (activityThemeRef != null) {
          ThemeData activityTheme = getThemeByRef(activityThemeRef);
          if (!myAddedThemes.contains(activityTheme) && fromProject == activityTheme.isProjectTheme()) {
            myAddedThemes.add(activityTheme);
            activityThemesList.add(activityTheme);
          }
        }
      }
    }

    Collections.sort(activityThemesList);
    resultList.addAll(activityThemesList);
  }

  private void collectProjectThemes(AndroidFacet facet, Collection<ThemeData> resultList) {
    List<ThemeData> newThemes = new ArrayList<ThemeData>();
    Map<String, ResourceElement> styleMap = buildStyleMap(facet);

    for (ResourceElement style : styleMap.values()) {
      if (isTheme(style, styleMap, new HashSet<ResourceElement>())) {
        String themeName = style.getName().getValue();
        if (themeName != null) {
          ThemeData theme = new ThemeData(themeName, true);
          if (myAddedThemes.add(theme)) {
            newThemes.add(theme);
          }
        }
      }
    }

    Collections.sort(newThemes);
    resultList.addAll(newThemes);
  }

  private void collectFrameworkThemes(AndroidFacet facet, @Nullable AndroidTargetData targetData) {
    List<ThemeData> frameworkThemes = new ArrayList<ThemeData>();
    collectThemesFromManifest(facet, frameworkThemes, false);
    if (targetData != null) {
      doCollectFrameworkThemes(facet, targetData, frameworkThemes);
    }
    if (frameworkThemes.size() > 0) {
      myThemes.add(FRAMEWORK);
      myThemes.addAll(frameworkThemes);
    }
  }

  private void doCollectFrameworkThemes(AndroidFacet facet,
                                        @NotNull AndroidTargetData targetData,
                                        List<ThemeData> themes) {
    List<String> frameworkThemeNames = new ArrayList<String>(targetData.getThemes(facet));
    Collections.sort(frameworkThemeNames);
    for (String themeName : frameworkThemeNames) {
      ThemeData themeData = new ThemeData(themeName, false);
      if (myAddedThemes.add(themeData)) {
        themes.add(themeData);
      }
    }
  }

  private static Map<String, ResourceElement> buildStyleMap(AndroidFacet facet) {
    Map<String, ResourceElement> result = new HashMap<String, ResourceElement>();
    List<ResourceElement> styles = facet.getLocalResourceManager().getValueResources(ResourceType.STYLE.getName());
    for (ResourceElement style : styles) {
      String styleName = style.getName().getValue();
      if (styleName != null) {
        result.put(styleName, style);
      }
    }
    return result;
  }

  private static boolean isTheme(ResourceElement resElement, Map<String, ResourceElement> styleMap, Set<ResourceElement> visitedElements) {
    if (!visitedElements.add(resElement)) {
      return false;
    }

    if (!(resElement instanceof Style)) {
      return false;
    }

    String styleName = resElement.getName().getValue();
    if (styleName == null) {
      return false;
    }

    ResourceValue parentStyleRef = ((Style)resElement).getParentStyle().getValue();
    String parentStyleName = null;
    boolean frameworkStyle = false;

    if (parentStyleRef != null) {
      String s = parentStyleRef.getResourceName();
      if (s != null) {
        parentStyleName = s;
        frameworkStyle = AndroidUtils.SYSTEM_RESOURCE_PACKAGE.equals(parentStyleRef.getPackage());
      }
    }

    if (parentStyleRef == null) {
      int index = styleName.indexOf('.');
      if (index >= 0) {
        parentStyleName = styleName.substring(0, index);
      }
    }

    if (parentStyleRef != null) {
      if (frameworkStyle) {
        return parentStyleName.equals("Theme") || parentStyleName.startsWith("Theme.");
      }
      else {
        ResourceElement parentStyle = styleMap.get(parentStyleName);
        if (parentStyle != null) {
          return isTheme(parentStyle, styleMap, visitedElements);
        }
      }
    }

    return false;
  }

  @NotNull
  private static ThemeData getThemeByRef(@NotNull String themeRef) {
    boolean isProjectTheme = !themeRef.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX);
    if (themeRef.startsWith(SdkConstants.STYLE_RESOURCE_PREFIX)) {
      themeRef = themeRef.substring(SdkConstants.STYLE_RESOURCE_PREFIX.length());
    }
    else if (themeRef.startsWith(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX)) {
      themeRef = themeRef.substring(SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX.length());
    }
    return new ThemeData(themeRef, isProjectTheme);
  }

  @NotNull
  private static String getDefaultTheme(IAndroidTarget target,
                                        IAndroidTarget renderingTarget,
                                        ScreenSize screenSize) {
    int targetApiLevel = target != null ? target.getVersion().getApiLevel() : 0;

    int renderingTargetApiLevel = renderingTarget != null
                                  ? renderingTarget.getVersion().getApiLevel()
                                  : targetApiLevel;

    return targetApiLevel >= 11 && renderingTargetApiLevel >= 11 && screenSize == ScreenSize.XLARGE
           ? SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme.Holo"
           : SdkConstants.ANDROID_STYLE_RESOURCE_PREFIX + "Theme";
  }
}