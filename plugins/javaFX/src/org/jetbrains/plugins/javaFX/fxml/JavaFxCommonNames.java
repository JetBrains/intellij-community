// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.plugins.javaFX.fxml;

import com.intellij.psi.PsiPrimitiveType;
import com.intellij.psi.PsiType;
import com.intellij.psi.PsiTypes;
import org.jetbrains.annotations.NonNls;

import java.util.HashMap;
import java.util.Map;

public final class JavaFxCommonNames {
  public static final @NonNls String JAVAFX_BEANS_PROPERTY = "javafx.beans.property.Property";
  public static final @NonNls String JAVAFX_COLLECTIONS_OBSERVABLE_LIST = "javafx.collections.ObservableList";
  public static final @NonNls String JAVAFX_COLLECTIONS_OBSERVABLE_SET = "javafx.collections.ObservableSet";
  public static final @NonNls String JAVAFX_COLLECTIONS_OBSERVABLE_MAP = "javafx.collections.ObservableMap";
  public static final @NonNls String JAVAFX_COLLECTIONS_OBSERVABLE_ARRAY = "javafx.collections.ObservableArray";
  public static final @NonNls String JAVAFX_ANCHOR_PANE = "javafx.scene.layout.AnchorPane";
  public static final @NonNls String JAVAFX_EVENT = "javafx.event.Event";
  public static final @NonNls String JAVAFX_BEANS_DEFAULT_PROPERTY = "javafx.beans.DefaultProperty";
  public static final @NonNls String JAVAFX_FXML_ANNOTATION = "javafx.fxml.FXML";
  public static final @NonNls String JAVAFX_BEANS_PROPERTY_OBJECT_PROPERTY = "javafx.beans.property.ObjectProperty";
  public static final @NonNls String JAVAFX_EVENT_EVENT_HANDLER = "javafx.event.EventHandler";
  public static final @NonNls String JAVAFX_SCENE_NODE = "javafx.scene.Node";
  public static final @NonNls String JAVAFX_SCENE_PAINT = "javafx.scene.paint.Paint";
  public static final @NonNls String JAVAFX_SCENE_COLOR = "javafx.scene.paint.Color";
  public static final @NonNls String JAVAFX_FXML_BUILDER = "javafx.util.Builder";
  public static final @NonNls String JAVAFX_BEANS_OBSERVABLE = "javafx.beans.Observable";
  public static final @NonNls String VALUE_OF = "valueOf";
  public static final @NonNls String GET_VALUE = "getValue";
  public static final @NonNls String VALUE = "value";
  public static final @NonNls String JAVAFX_FXML_FXMLLOADER = "javafx.fxml.FXMLLoader";
  public static final @NonNls String JAVAFX_BEANS_VALUE_OBSERVABLE_VALUE = "javafx.beans.value.ObservableValue";
  public static final @NonNls String JAVAFX_BEANS_VALUE_WRITABLE_VALUE = "javafx.beans.value.WritableValue";
  public static final @NonNls String JAVAFX_SCENE_LAYOUT_PANE = "javafx.scene.layout.Pane";
  public static final @NonNls String JAVAFX_BEANS_NAMED_ARG = "javafx.beans.NamedArg";
  public static final @NonNls String JAVAFX_BEANS_PROPERTY_SIMPLE_STRING_PROPERTY = "javafx.beans.property.SimpleStringProperty";
  public static final @NonNls String JAVAFX_BEANS_PROPERTY_SIMPLE_LIST_PROPERTY = "javafx.beans.property.SimpleListProperty";
  public static final @NonNls String JAVAFX_BEANS_PROPERTY_SIMPLE_OBJECT_PROPERTY = "javafx.beans.property.SimpleObjectProperty";

  public static final @NonNls String PROPERTY_METHOD_SUFFIX = "Property";

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

  public static final @NonNls String JAVA_FX_PARENT = "javafx.scene.Parent";
  public static final @NonNls String JAVA_FX_SCENE = "javafx.scene.Scene";
  public static final @NonNls String JAVAFX_APPLICATION_APPLICATION = "javafx.application.Application";

  public static final Map<PsiPrimitiveType, String> ourObservablePrimitiveWrappers = new HashMap<>();
  static {
    ourObservablePrimitiveWrappers.put(PsiTypes.intType(), "javafx.beans.property.SimpleIntegerProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.longType(), "javafx.beans.property.SimpleLongProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.floatType(), "javafx.beans.property.SimpleFloatProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.doubleType(), "javafx.beans.property.SimpleDoubleProperty");
    ourObservablePrimitiveWrappers.put(PsiTypes.booleanType(), "javafx.beans.property.SimpleBooleanProperty");
  }
}
