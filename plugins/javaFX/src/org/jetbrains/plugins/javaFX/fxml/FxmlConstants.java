/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.jetbrains.plugins.javaFX.fxml;

import org.jetbrains.annotations.NonNls;

import java.util.*;

/**
 * User: anna
 * Date: 1/14/13
 */
public class FxmlConstants {
  @NonNls public static final String FX_CONTROLLER = "fx:controller";
  @NonNls public static final String FX_ID = "fx:id";
  @NonNls public static final String VALUE = "value";
  @NonNls public static final String FX_VALUE = "fx:value";
  @NonNls public static final String FX_FACTORY = "fx:factory";
  @NonNls public static final String FX_CONSTANT = "fx:constant";
  
  @NonNls public static final String FX_REFERENCE = "fx:reference";
  @NonNls public static final String FX_COPY = "fx:copy";
  @NonNls public static final String FX_DEFINE = "fx:define";
  @NonNls public static final String FX_SCRIPT = "fx:script";
  @NonNls public static final String FX_INCLUDE = "fx:include";
  @NonNls public static final String FX_ROOT = "fx:root";
  @NonNls public static final String TYPE = "type";
  @NonNls public static final String RESOURCES = "resources";
  @NonNls public static final String CHARSET = "charset";

  @NonNls public static final String STYLE_CLASS = "styleClass";
  @NonNls public static final String STYLESHEETS = "stylesheets";

  public static final List<String> FX_DEFAULT_PROPERTIES = Arrays.asList(FX_ID, FX_CONTROLLER, VALUE, FX_VALUE, FX_FACTORY, FX_CONSTANT);
  public static final List<String> FX_DEFAULT_ELEMENTS = Arrays.asList(FX_INCLUDE, FX_REFERENCE, FX_COPY, FX_DEFINE, FX_SCRIPT, FX_ROOT);

  public static final String FX_ELEMENT_SOURCE = "source";

  public static final Map<String, List<String>> FX_ELEMENT_ATTRIBUTES = new HashMap<String, List<String>>();
  static {
    FX_ELEMENT_ATTRIBUTES.put(FX_INCLUDE, Arrays.asList(FX_ELEMENT_SOURCE, FX_ID, RESOURCES, CHARSET));
    FX_ELEMENT_ATTRIBUTES.put(FX_REFERENCE, Collections.singletonList(FX_ELEMENT_SOURCE));
    FX_ELEMENT_ATTRIBUTES.put(FX_COPY, Collections.singletonList(FX_ELEMENT_SOURCE));
    FX_ELEMENT_ATTRIBUTES.put(FX_SCRIPT, Collections.singletonList(FX_ELEMENT_SOURCE));
    FX_ELEMENT_ATTRIBUTES.put(FX_ROOT, Collections.singletonList(TYPE));
  }

  public static final Map<String, List<String>> FX_REQUIRED_ELEMENT_ATTRIBUTES = new HashMap<String, List<String>>();

  static {
    FX_REQUIRED_ELEMENT_ATTRIBUTES.put(FX_INCLUDE, Collections.singletonList(FX_ELEMENT_SOURCE));
    FX_REQUIRED_ELEMENT_ATTRIBUTES.put(FX_REFERENCE, Collections.singletonList(FX_ELEMENT_SOURCE));
    FX_REQUIRED_ELEMENT_ATTRIBUTES.put(FX_COPY, Collections.singletonList(FX_ELEMENT_SOURCE));
    FX_REQUIRED_ELEMENT_ATTRIBUTES.put(FX_ROOT, Collections.singletonList(TYPE));
  }

}
