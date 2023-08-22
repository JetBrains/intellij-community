// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public final class JavaFxCommonNames {
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
  @NonNls public static final String JAVAFX_BEANS_PROPERTY_SIMPLE_STRING_PROPERTY = "javafx.beans.property.SimpleStringProperty";
  @NonNls public static final String JAVAFX_BEANS_PROPERTY_SIMPLE_LIST_PROPERTY = "javafx.beans.property.SimpleListProperty";
  @NonNls public static final String JAVAFX_BEANS_PROPERTY_SIMPLE_OBJECT_PROPERTY = "javafx.beans.property.SimpleObjectProperty";

  @NonNls public static final String PROPERTY_METHOD_SUFFIX = "Property";

  public static final Map<String, PsiType> ourWritableMap = new HashMap<>();
  static {
    ourWritableMap.put("javafx.beans.value.WritableBooleanValue", PsiTypes.booleanType());
    ourWritableMap.put("javafx.beans.value.WritableIntegerValue", PsiTypes.intType());
    ourWritableMap.put("javafx.beans.value.WritableFloatValue", PsiTypes.floatType());
    ourWritableMap.put("javafx.beans.value.WritableLongValue", PsiTypes.longType());
    ourWritableMap.put("javafx.beans.value.WritableDoubleValue", PsiTypes.doubleType());
  }

  public static final Map<String, PsiType> ourReadOnlyMap = new HashMap<>();
  static {
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyBooleanProperty", PsiTypes.booleanType());
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyIntegerProperty", PsiTypes.intType());
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyFloatProperty", PsiTypes.floatType());
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyLongProperty", PsiTypes.longType());
    ourReadOnlyMap.put("javafx.beans.property.ReadOnlyDoubleProperty", PsiTypes.doubleType());
  }

  @NonNls public static final String JAVA_FX_PARENT = "javafx.scene.Parent";
  @NonNls public static final String JAVA_FX_SCENE = "javafx.scene.Scene";
  @NonNls public static final String JAVAFX_APPLICATION_APPLICATION = "javafx.application.Application";

  public static final Map<PsiPrimitiveType, String> ourObservablePrimitiveWrappers = new HashMap<>();
  static {
    ourObservablePrimitiveWrappers.put(PsiTypes.intType(), "javafx.beans.property.SimpleIntegerProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.longType(), "javafx.beans.property.SimpleLongProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.floatType(), "javafx.beans.property.SimpleFloatProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.doubleType(), "javafx.beans.property.SimpleDoubleProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.booleanType(), "javafx.beans.property.SimpleBooleanProperty");
  }
}
