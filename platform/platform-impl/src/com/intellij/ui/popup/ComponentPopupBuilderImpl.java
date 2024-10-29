// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.popup;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.popup.*;
import com.intellij.openapi.util.*;
import com.intellij.ui.ActiveComponent;
import com.intellij.util.BooleanFunction;
import com.intellij.util.Processor;
import com.intellij.util.ui.EmptyIcon;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.util.List;
import java.util.*;
import java.util.function.Supplier;

/**
 * @author anna
 */
@ApiStatus.Internal
public class ComponentPopupBuilderImpl implements ComponentPopupBuilder {
  private @NlsContexts.PopupTitle String myTitle = "";
  private boolean myResizable;
  private boolean myMovable;
  private final JComponent myComponent;
  private final JComponent myPreferredFocusedComponent;
  private boolean myRequestFocus;
  private String myDimensionServiceKey;
  private Computable<Boolean> myCallback;
  private Project myProject;
  private boolean myCancelOnClickOutside = true;
  private boolean myCancelOnWindowDeactivation = true;
  private final Set<JBPopupListener> myListeners = new LinkedHashSet<>();
  private boolean myUseDimServiceForXYLocation;

  private IconButton myCancelButton;
  private MouseChecker myCancelOnMouseOutCallback;
  private boolean myCancelOnWindow;
  private ActiveIcon myTitleIcon = new ActiveIcon(EmptyIcon.ICON_0);
  private boolean myCancelKeyEnabled = true;
  private boolean myLocateByContent;
  private boolean myPlaceWithinScreen = true;
  private Processor<? super JBPopup> myPinCallback;
  private Dimension myMinSize;
  private boolean myStretchToOwnerWidth = false;
  private boolean myStretchToOwnerHeight = false;
  private MaskProvider myMaskProvider;
  private float myAlpha;
  private List<Object> myUserData;

  private boolean myInStack = true;
  private boolean myModalContext = true;
  private Component[] myFocusOwners = new Component[0];

  private @NlsContexts.PopupAdvertisement String myAd;
  private JComponent myAdvertiser;
  private boolean myShowShadow = true;
  private boolean myShowBorder = true;
  private boolean myFocusable = true;
  private ActiveComponent myCommandButton;
  private List<? extends Pair<ActionListener, KeyStroke>> myKeyboardActions = Collections.emptyList();
  private Component mySettingsButtons;
  private boolean myMayBeParent;
  private int myAdAlignment = SwingConstants.LEFT;
  private BooleanFunction<? super KeyEvent> myKeyEventHandler;
  private Color myBorderColor;
  private boolean myNormalWindowLevel;
  private @Nullable Runnable myOkHandler;

  public ComponentPopupBuilderImpl(@NotNull JComponent component, JComponent preferredFocusedComponent) {
    myComponent = component;
    myPreferredFocusedComponent = preferredFocusedComponent;
  }

  @Override
  public @NotNull ComponentPopupBuilder setMayBeParent(boolean mayBeParent) {
    myMayBeParent = mayBeParent;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setTitle(@NlsContexts.PopupTitle String title) {
    myTitle = title;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setResizable(final boolean resizable) {
    myResizable = resizable;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setMovable(final boolean movable) {
    myMovable = movable;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCancelOnClickOutside(final boolean cancel) {
    myCancelOnClickOutside = cancel;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCancelOnMouseOutCallback(final @NotNull MouseChecker shouldCancel) {
    myCancelOnMouseOutCallback = shouldCancel;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder addListener(final @NotNull JBPopupListener listener) {
    myListeners.add(listener);
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setRequestFocus(final boolean requestFocus) {
    myRequestFocus = requestFocus;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setFocusable(final boolean focusable) {
    myFocusable = focusable;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setDimensionServiceKey(final Project project, final String key, final boolean useForXYLocation) {
    myDimensionServiceKey = key;
    myUseDimServiceForXYLocation = useForXYLocation;
    myProject = project;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCancelCallback(final @NotNull Computable<Boolean> shouldProceed) {
    myCallback = shouldProceed;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCancelButton(final @NotNull IconButton cancelButton) {
    myCancelButton = cancelButton;
    return this;
  }
  @Override
  public @NotNull ComponentPopupBuilder setCommandButton(@NotNull ActiveComponent button) {
    myCommandButton = button;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCouldPin(final @Nullable Processor<? super JBPopup> callback) {
    myPinCallback = callback;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setKeyboardActions(@NotNull List<? extends Pair<ActionListener, KeyStroke>> keyboardActions) {
    myKeyboardActions = keyboardActions;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setSettingButtons(@NotNull Component button) {
    mySettingsButtons = button;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCancelOnOtherWindowOpen(final boolean cancelOnWindow) {
    myCancelOnWindow = cancelOnWindow;
    return this;
  }

  @Override
  public ComponentPopupBuilder setCancelOnWindowDeactivation(boolean cancelOnWindowDeactivation) {
    myCancelOnWindowDeactivation = cancelOnWindowDeactivation;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setKeyEventHandler(@NotNull BooleanFunction<? super KeyEvent> handler) {
    myKeyEventHandler = handler;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setProject(Project project) {
    myProject = project;
    return this;
  }

  @Override
  public @NotNull JBPopup createPopup() {
    return createPopup(DEFAULT_POPUP_SUPPLIER);
  }

  public @NotNull AbstractPopup createPopup(Supplier<? extends AbstractPopup> popupSupplier) {
    AbstractPopup popup = popupSupplier.get().init(
      myProject, myComponent, myPreferredFocusedComponent, myRequestFocus, myFocusable, myMovable, myDimensionServiceKey,
      myResizable, myTitle, myCallback, myCancelOnClickOutside, myListeners, myUseDimServiceForXYLocation, myCommandButton,
      myCancelButton, myCancelOnMouseOutCallback, myCancelOnWindow, myTitleIcon, myCancelKeyEnabled, myLocateByContent,
      myPlaceWithinScreen, myMinSize, myAlpha, myMaskProvider, myInStack, myModalContext, myFocusOwners, myAd, myAdAlignment,
      false, myKeyboardActions, mySettingsButtons, myPinCallback, myMayBeParent,
      myShowShadow, myShowBorder, myBorderColor, myCancelOnWindowDeactivation, myKeyEventHandler
    );

    popup.setStretchToOwnerWidth(myStretchToOwnerWidth);
    popup.setStretchToOwnerHeight(myStretchToOwnerHeight);

    popup.setNormalWindowLevel(myNormalWindowLevel);
    popup.setOkHandler(myOkHandler);
    if (myAdvertiser != null) {
      popup.setFooterComponent(myAdvertiser);
    }

    if (myUserData != null) {
      popup.setUserData(myUserData);
    }
    Disposer.register(ApplicationManager.getApplication(), popup);
    return popup;
  }


  private final Supplier<AbstractPopup> DEFAULT_POPUP_SUPPLIER = () -> new AbstractPopup();

  @Override
  public @NotNull ComponentPopupBuilder setRequestFocusCondition(@NotNull Project project, @NotNull Condition<? super Project> condition) {
    myRequestFocus = condition.value(project);
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setTitleIcon(final @NotNull ActiveIcon icon) {
    myTitleIcon = icon;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setCancelKeyEnabled(final boolean enabled) {
    myCancelKeyEnabled = enabled;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setLocateByContent(final boolean byContent) {
    myLocateByContent = byContent;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setLocateWithinScreenBounds(final boolean within) {
    myPlaceWithinScreen = within;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setMinSize(final Dimension minSize) {
    myMinSize = minSize;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setStretchToOwnerWidth(boolean stretchToOwnerWidth) {
    myStretchToOwnerWidth = stretchToOwnerWidth;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setStretchToOwnerHeight(boolean stretchToOwnerHeight) {
    myStretchToOwnerHeight = stretchToOwnerHeight;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setMaskProvider(MaskProvider maskProvider) {
    myMaskProvider = maskProvider;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setAlpha(final float alpha) {
    myAlpha = alpha;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setBelongsToGlobalPopupStack(final boolean isInStack) {
    myInStack = isInStack;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder addUserData(final Object object) {
    if (myUserData == null) {
      myUserData = new ArrayList<>();
    }
    myUserData.add(object);
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setModalContext(final boolean modal) {
    myModalContext = modal;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setFocusOwners(final Component @NotNull [] focusOwners) {
    myFocusOwners = focusOwners;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setAdText(final @Nullable String text) {
    return setAdText(text, SwingConstants.LEFT);
  }

  @Override
  public @NotNull ComponentPopupBuilder setAdText(@Nullable String text, int textAlignment) {
    myAd = text;
    myAdAlignment = textAlignment;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setAdvertiser(@Nullable JComponent advertiser) {
    myAdvertiser = advertiser;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setShowShadow(boolean show) {
    myShowShadow = show;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setShowBorder(boolean show) {
    myShowBorder = show;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setNormalWindowLevel(boolean b) {
    myNormalWindowLevel = b;
    return this;
  }

  @Override
  public @NotNull ComponentPopupBuilder setBorderColor(Color color) {
    myBorderColor = color;
    return this;
  }
  
  @Override
  public @NotNull ComponentPopupBuilder setOkHandler(@Nullable Runnable okHandler) {
    myOkHandler = okHandler;
    return this;
  }
}
