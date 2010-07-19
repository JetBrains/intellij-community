/*
 * Copyright 2000-2009 JetBrains s.r.o.
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

package com.intellij.ui.popup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.util.Condition;
import com.intellij.openapi.util.Disposer;
import com.intellij.openapi.util.Pair;
import com.intellij.util.Processor;
import com.intellij.util.ui.EmptyIcon;
import com.intellij.ui.InplaceButton;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.util.*;
import java.util.List;

/**
 * User: anna
 * Date: 15-Mar-2006
 */
public class ComponentPopupBuilderImpl implements ComponentPopupBuilder {
  private String myTitle = "";
  private boolean myResizable;
  private boolean myMovable;
  private final JComponent myComponent;
  private final JComponent myPrefferedFocusedComponent;
  private boolean myRequestFocus;
  private boolean myForceHeavyweight;
  private String myDimensionServiceKey = null;
  private Computable<Boolean> myCallback = null;
  private Project myProject;
  private boolean myCancelOnClickOutside = true;
  private final Set<JBPopupListener> myListeners = new LinkedHashSet<JBPopupListener>();
  private boolean myUseDimSevriceForXYLocation;

  private IconButton myCancelButton;
  private MouseChecker myCancelOnMouseOutCallback;
  private boolean myCancelOnWindow;
  private ActiveIcon myTitleIcon = new ActiveIcon(new EmptyIcon(0));
  private boolean myCancelKeyEnabled = true;
  private boolean myLocateByContent = false;
  private boolean myPlacewithinScreen = true;
  private Processor<JBPopup> myPinCallback = null;
  private Dimension myMinSize;
  private MaskProvider myMaskProvider;
  private float myAlpha;
  private ArrayList<Object> myUserData;

  private boolean myInStack = true;
  private boolean myModalContext = true;
  private Component[] myFocusOwners = new Component[0];

  private String myAd;
  private boolean myShowShadow = true;
  private boolean myFocusable = true;
  private boolean myHeaderAlwaysFocusable;
  private InplaceButton myCommandButton;
  private List<Pair<ActionListener, KeyStroke>> myKeyboardActions = Collections.emptyList();
  private Component mySettingsButtons;
  private boolean myMayBeParent;

  public ComponentPopupBuilderImpl(final JComponent component,
                                   final JComponent prefferedFocusedComponent) {
    myComponent = component;
    myPrefferedFocusedComponent = prefferedFocusedComponent;
  }

  public ComponentPopupBuilder setMayBeParent(boolean mayBeParent) {
    myMayBeParent = mayBeParent;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setTitle(String title) {
    myTitle = title;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setResizable(final boolean resizable) {
    myResizable = resizable;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setMovable(final boolean movable) {
    myMovable = movable;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelOnClickOutside(final boolean cancel) {
    myCancelOnClickOutside = cancel;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelOnMouseOutCallback(final MouseChecker shouldCancel) {
    myCancelOnMouseOutCallback = shouldCancel;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder addListener(final JBPopupListener listener) {
    myListeners.add(listener);
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setRequestFocus(final boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setFocusable(final boolean focusable) {
    myFocusable = focusable;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setForceHeavyweight(final boolean forceHeavyweight) {
    myForceHeavyweight = forceHeavyweight;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setDimensionServiceKey(final Project project, final String dimensionServiceKey, final boolean useForXYLocation) {
    myDimensionServiceKey = dimensionServiceKey;
    myUseDimSevriceForXYLocation = useForXYLocation;
    myProject = project;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelCallback(final Computable<Boolean> shouldProceed) {
    myCallback = shouldProceed;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelButton(@NotNull final IconButton cancelButton) {
    myCancelButton = cancelButton;
    return this;
  }
  @NotNull
  public ComponentPopupBuilder setCommandButton(@NotNull InplaceButton button) {
    myCommandButton = button;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCouldPin(@Nullable final Processor<JBPopup> callback) {
    myPinCallback = callback;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setKeyboardActions(@NotNull List<Pair<ActionListener, KeyStroke>> keyboardActions) {
    myKeyboardActions = keyboardActions;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setSettingButtons(@NotNull Component button) {
    mySettingsButtons = button;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelOnOtherWindowOpen(final boolean cancelOnWindow) {
    myCancelOnWindow = cancelOnWindow;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setProject(Project project) {
    myProject = project;
    return this;
  }

  @NotNull
  public JBPopup createPopup() {
    final AbstractPopup popup = new AbstractPopup().init(myProject, myComponent, myPrefferedFocusedComponent, myRequestFocus, myFocusable, myForceHeavyweight,
                                              myMovable, myDimensionServiceKey, myResizable, myTitle,
                                              myCallback, myCancelOnClickOutside, myListeners, myUseDimSevriceForXYLocation, myCommandButton,
                                              myCancelButton,
                                              myCancelOnMouseOutCallback, myCancelOnWindow, myTitleIcon, myCancelKeyEnabled, myLocateByContent,
                                              myPlacewithinScreen, myMinSize, myAlpha, myMaskProvider, myInStack, myModalContext, myFocusOwners, myAd,
                                              myHeaderAlwaysFocusable, myKeyboardActions, mySettingsButtons, myPinCallback, myMayBeParent,
                                              myShowShadow);
    if (myUserData != null) {
      popup.setUserData(myUserData);
    }
    //default disposable parent
    Disposer.register(ApplicationManager.getApplication(), popup);
    return popup;
  }

  @NotNull
  public ComponentPopupBuilder setRequestFocusCondition(Project project, Condition<Project> condition) {
    myRequestFocus = condition.value(project);
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setTitleIcon(@NotNull final ActiveIcon icon) {
    myTitleIcon = icon;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setCancelKeyEnabled(final boolean enabled) {
    myCancelKeyEnabled = enabled;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setLocateByContent(final boolean byContent) {
    myLocateByContent = byContent;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setLocateWithinScreenBounds(final boolean within) {
    myPlacewithinScreen = within;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setMinSize(final Dimension minSize) {
    myMinSize = minSize;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setMaskProvider(MaskProvider maskProvider) {
    myMaskProvider = maskProvider;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setAlpha(final float alpha) {
    myAlpha = alpha;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setBelongsToGlobalPopupStack(final boolean isInStack) {
    myInStack = isInStack;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder addUserData(final Object object) {
    if (myUserData == null) {
      myUserData = new ArrayList<Object>();
    }
    myUserData.add(object);
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setModalContext(final boolean modal) {
    myModalContext = modal;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setFocusOwners(@NotNull final Component[] focusOwners) {
    myFocusOwners = focusOwners;
    return this;
  }

  @NotNull
  public ComponentPopupBuilder setAdText(@Nullable final String text) {
    myAd = text;
    return this;
  }

  @NotNull
  @Override
  public ComponentPopupBuilder setShowShadow(boolean show) {
    myShowShadow = show;
    return this;
  }
}
