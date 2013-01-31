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

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
  
  public static final List<String> FX_DEFAULT_PROPERTIES = Arrays.asList(FX_ID, FX_CONTROLLER, VALUE, FX_VALUE, FX_FACTORY, FX_CONSTANT);
  public static final List<String> FX_DEFAULT_ELEMENTS = Arrays.asList(FX_INCLUDE, FX_REFERENCE, FX_COPY, FX_DEFINE, FX_SCRIPT);

  public static final String FX_ELEMENT_SOURCE = "source";

  public static final Map<String, List<String>> FX_ELEMENT_ATTRIBUTES = new HashMap<String, List<String>>();
  static {
    FX_ELEMENT_ATTRIBUTES.put(FX_INCLUDE, Arrays.asList(FX_ELEMENT_SOURCE));
    FX_ELEMENT_ATTRIBUTES.put(FX_REFERENCE, Arrays.asList(FX_ELEMENT_SOURCE));
  }
}
