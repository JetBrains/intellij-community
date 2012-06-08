/*
 * Copyright 2000-2010 JetBrains s.r.o.
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

package org.jetbrains.android.dom.xml;

import com.intellij.util.containers.HashMap;
import org.jetbrains.android.dom.AndroidDomExtender;
import org.jetbrains.android.dom.AndroidDomUtil;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 19, 2009
 * Time: 6:44:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidXmlResourcesUtil {
  @NonNls public static final String APPWIDGET_PROVIDER_TAG_NAME = "appwidget-provider";
  @NonNls public static final String SEARCHABLE_TAG_NAME = "searchable";
  @NonNls public static final String KEYBOARD_TAG_NAME = "Keyboard";
  @NonNls public static final String DEVICE_ADMIN_TAG_NAME = "device-admin";
  @NonNls public static final String ACCOUNT_AUTHENTICATOR_TAG_NAME = "account-authenticator";
  @NonNls public static final String PREFERENCE_HEADERS_TAG_NAME = "preference-headers";

  public static final Map<String, String> SPECIAL_STYLEABLE_NAMES = new HashMap<String, String>();
  public static final String PREFERENCE_CLASS_NAME = "android.preference.Preference";
  private static final String[] ROOT_TAGS =
    {APPWIDGET_PROVIDER_TAG_NAME, SEARCHABLE_TAG_NAME, KEYBOARD_TAG_NAME, DEVICE_ADMIN_TAG_NAME, ACCOUNT_AUTHENTICATOR_TAG_NAME,
      PREFERENCE_HEADERS_TAG_NAME};

  private static final Set<String> ROOT_TAGS_SET;

  static {
    SPECIAL_STYLEABLE_NAMES.put(APPWIDGET_PROVIDER_TAG_NAME, "AppWidgetProviderInfo");
    SPECIAL_STYLEABLE_NAMES.put(SEARCHABLE_TAG_NAME, "Searchable");
    SPECIAL_STYLEABLE_NAMES.put("actionkey", "SearchableActionKey");
    SPECIAL_STYLEABLE_NAMES.put("intent", "Intent");
    SPECIAL_STYLEABLE_NAMES.put(KEYBOARD_TAG_NAME, "Keyboard");
    SPECIAL_STYLEABLE_NAMES.put("Row", "Keyboard_Row");
    SPECIAL_STYLEABLE_NAMES.put("Key", "Keyboard_Key");
    SPECIAL_STYLEABLE_NAMES.put(DEVICE_ADMIN_TAG_NAME, "DeviceAdmin");
    SPECIAL_STYLEABLE_NAMES.put(ACCOUNT_AUTHENTICATOR_TAG_NAME, "AccountAuthenticator");
    SPECIAL_STYLEABLE_NAMES.put("header", "PreferenceHeader");

    ROOT_TAGS_SET = new HashSet<String>(Arrays.asList(ROOT_TAGS));
  }

  private AndroidXmlResourcesUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    List<String> result = new ArrayList<String>();
    result.addAll(AndroidDomUtil.removeUnambigiousNames(
      AndroidDomExtender.getPreferencesClassMap(facet)));
    result.addAll(Arrays.asList(ROOT_TAGS));

    return result;
  }

  public static boolean isSupportedRootTag(@NotNull AndroidFacet facet, @NotNull String rootTagName) {
    return ROOT_TAGS_SET.contains(rootTagName) ||
           AndroidDomExtender.getPreferencesClassMap(facet).keySet().contains(rootTagName);
  }
}
