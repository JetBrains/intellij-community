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

import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

/**
 * User: anna
 * Date: 1/16/13
 */
public class JavaFxCommonNames {
  @NonNls public static final String JAVAFX_BEANS_PROPERTY = "javafx.beans.property.Property";
  @NonNls public static final String JAVAFX_COLLECTIONS_OBSERVABLE_LIST = "javafx.collections.ObservableList";
  @NonNls public static final String JAVAFX_COLLECTIONS_OBSERVABLE_SET = "javafx.collections.ObservableSet";
  @NonNls public static final String JAVAFX_COLLECTIONS_OBSERVABLE_MAP = "javafx.collections.ObservableMap";
  @NonNls public static final String JAVAFX_COLLECTIONS_OBSERVABLE_ARRAY = "javafx.collections.ObservableArray";
  @NonNls public static final String JAVAFX_ANCHOR_PANE = "javafx.scene.layout.AnchorPane";
  @NonNls public static final String JAVAFX_EVENT = "javafx.event.Event";
  @NonNls public static final String JAVAFX_BEANS_DEFAULT_PROPERTY = "javafx.beans.DefaultProperty";
  @NonNls public static final String JAVAFX_FXML_ANNOTATION = "javafx.fxml.FXML";
  @NonNls public static final String JAVAFX_BEANS_PROPERTY_OBJECT_PROPERTY = "javafx.beans.property.ObjectProperty";
  @NonNls public static final String JAVAFX_EVENT_EVENT_HANDLER = "javafx.event.EventHandler";
  @NonNls public static final String JAVAFX_SCENE_NODE = "javafx.scene.Node";
  @NonNls public static final String JAVAFX_SCENE_PAINT = "javafx.scene.paint.Paint";
  @NonNls public static final String JAVAFX_SCENE_COLOR = "javafx.scene.paint.Color";
  @NonNls public static final String JAVAFX_FXML_BUILDER = "javafx.util.Builder";
  @NonNls public static final String JAVAFX_BEANS_OBSERVABLE = "javafx.beans.Observable";
  @NonNls public static final String VALUE_OF = "valueOf";
  @NonNls public static final String GET_VALUE = "getValue";
  @NonNls public static final String VALUE = "value";
  @NonNls public static final String JAVAFX_FXML_FXMLLOADER = "javafx.fxml.FXMLLoader";
  @NonNls public static final String JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE = "javafx.beans.value.ObservableValue";
  @NonNls public static final String JAVAFX_BEANS_VALUE_WRITABLE_VALUE = "javafx.beans.value.WritableValue";
  @NonNls public static final String JAVAFX_SCENE_LAYOUT_PANE = "javafx.scene.layout.Pane";
  @NonNls public static final String JAVAFX_BEANS_NAMED_ARG = "javafx.beans.NamedArg";

  @NonNls public static final String PROPERTY_METHOD_SUFFIX = "Property";

  public static final Map<String, PsiType> ourWritableMap = new HashMap<>();
  static {
    ourWritableMap.put("javafx.beans.value.WritableBooleanValue", PsiType.BOOLEAN);
    ourWritableMap.put("javafx.beans.value.WritableIntegerValue", PsiType.INT);
    ourWritableMap.put("javafx.beans.value.WritableFloatValue", PsiType.FLOAT);
    ourWritableMap.put("javafx.beans.value.WritableLongValue", PsiType.LONG);
    ourWritableMap.put("javafx.beans.value.WritableDoubleValue", PsiType.DOUBLE);
  }

  public static final Map<String, PsiType> ourReadOnlyMap = new HashMap<>();
  static {
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyBooleanProperty", PsiType.BOOLEAN);
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyIntegerProperty", PsiType.INT);
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyFloatProperty", PsiType.FLOAT);
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyLongProperty", PsiType.LONG);
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyDoubleProperty", PsiType.DOUBLE);
  }

  @NonNls public static final String JAVA_FX_PARENT = "javafx.scene.Parent";
  @NonNls public static final String JAVA_FX_SCENE = "javafx.scene.Scene";
  @NonNls public static final String JAVAFX_APPLICATION_APPLICATION = "javafx.application.Application";
}
