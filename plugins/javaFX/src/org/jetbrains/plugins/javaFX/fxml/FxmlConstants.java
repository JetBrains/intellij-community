// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.openapi.util.text.StringUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Map;
import java.util.Set;

public final class FxmlConstants {
  public static final @NonNls String FX_CONTROLLER = "fx:controller";
  public static final @NonNls String FX_ID = "fx:id";
  public static final @NonNls String VALUE = "value";
  public static final @NonNls String FX_VALUE = "fx:value";
  public static final @NonNls String FX_FACTORY = "fx:factory";
  public static final @NonNls String FX_CONSTANT = "fx:constant";

  public static final @NonNls String FX_REFERENCE = "fx:reference";
  public static final @NonNls String FX_COPY = "fx:copy";
  public static final @NonNls String FX_DEFINE = "fx:define";
  public static final @NonNls String FX_SCRIPT = "fx:script";
  public static final @NonNls String FX_INCLUDE = "fx:include";
  public static final @NonNls String FX_ROOT = "fx:root";
  public static final @NonNls String TYPE = "type";
  public static final @NonNls String RESOURCES = "resources";
  public static final @NonNls String CHARSET = "charset";
  public static final @NonNls String CONTROLLER = "controller";
  public static final @NonNls String CONTROLLER_SUFFIX = "Controller";

  public static final @NonNls String STYLE_CLASS = "styleClass";
  public static final @NonNls String STYLESHEETS = "stylesheets";
  public static final @NonNls String URL_ATTR = "url";
  public static final @NonNls String URL_TAG = "URL";

  public static final @NonNls String SOURCE = "source";

  public static final @NonNls String NULL_EXPRESSION = "${null}";
  private static final @NonNls String NULL_VALUE = "$null";

  public static final Set<String> FX_BUILT_IN_ATTRIBUTES = Set.of(FX_ID, FX_CONTROLLER, VALUE, FX_VALUE, FX_FACTORY, FX_CONSTANT);

  public static final Set<String> FX_BUILT_IN_TAGS = Set.of(FX_INCLUDE, FX_REFERENCE, FX_COPY, FX_DEFINE, FX_SCRIPT);

  public static final Map<String, List<String>> FX_BUILT_IN_TAG_SUPPORTED_ATTRIBUTES =
    Map.of(FX_INCLUDE, List.of(SOURCE, FX_ID, RESOURCES, CHARSET),
           FX_REFERENCE, List.of(SOURCE),
           FX_COPY, List.of(SOURCE),
           FX_SCRIPT, List.of(SOURCE));

  public static final Map<String, List<String>> FX_BUILT_IN_TAG_REQUIRED_ATTRIBUTES =
    Map.of(FX_INCLUDE, List.of(SOURCE),
           FX_REFERENCE, List.of(SOURCE),
           FX_COPY, List.of(SOURCE));

  public static boolean isNullValue(@NotNull String value) {
    return NULL_VALUE.equals(StringUtil.trimTrailing(value));
  }
}
