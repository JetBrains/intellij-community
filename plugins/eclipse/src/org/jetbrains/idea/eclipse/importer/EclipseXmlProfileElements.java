// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.eclipse.importer;

import org.jetbrains.annotations.NonNls;

public interface EclipseXmlProfileElements {
  @NonNls String PROFILES_TAG = "profiles";
  @NonNls String PROFILE_TAG = "profile";
  String NAME_ATTR = "name";
  @NonNls String SETTING_TAG = "setting";
  String ID_ATTR = "id";
  String VALUE_ATTR = "value";
  String VERSION_ATTR = "version";
  /* Exporting IDEA code style using an older profile is OK, as Eclipse maintains backwards compatibility.
     Importing a different version may produce worse results, as unrecognized IDs from the profile will be ignored. */
  String VERSION_VALUE = "21";
  String PROFILE_KIND_ATTR = "kind";
  String PROFILE_KIND_VALUE = "CodeFormatterProfile";
}
