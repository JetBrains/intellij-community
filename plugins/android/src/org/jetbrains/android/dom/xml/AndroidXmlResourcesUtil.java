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
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: Eugene.Kudelevsky
 * Date: Jun 19, 2009
 * Time: 6:44:28 PM
 * To change this template use File | Settings | File Templates.
 */
public class AndroidXmlResourcesUtil {
  public static final Map<String, String> SPECIAL_STYLEABLE_NAMES = new HashMap<String, String>();
  public static final String PREFERENCE_CLASS_NAME = "android.preference.Preference";

  static {
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("appwidget-provider", "AppWidgetProviderInfo");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("searchable", "Searchable");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("actionkey", "SearchableActionKey");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("intent", "Intent");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("Keyboard", "Keyboard");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("Row", "Keyboard_Row");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("Key", "Keyboard_Key");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("device-admin", "DeviceAdmin");
    AndroidXmlResourcesUtil.SPECIAL_STYLEABLE_NAMES.put("account-authenticator", "AccountAuthenticator");
  }

  private AndroidXmlResourcesUtil() {
  }

  @NotNull
  public static List<String> getPossibleRoots(@NotNull AndroidFacet facet) {
    List<String> result = new ArrayList<String>();
    result.addAll(AndroidDomExtender.getPreferencesClassMap(facet).keySet());
    result.addAll(SPECIAL_STYLEABLE_NAMES.keySet());
    result.add("Keyboard");
    return result;
  }
}
