// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.impl.NonBlockingReadActionImpl;
import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.openapi.ui.popup.ListPopup;
import com.intellij.ui.components.JBList;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.ui.UIUtil;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.function.Function;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.Assert.*;

/**
 * Interceptor for the next popup chooser
 */
public class ChooserInterceptor extends UiInterceptors.UiInterceptor<JBPopup> {
  final List<String> myOptions;
  final Pattern myToSelect;

  @Nullable Function<List<String>, String> myChooseOption;

  /**
   * Create an interceptor which will assert the expected options and select the given option when chooser will appear
   *
   * @param expectedOptions expected options to assert; pass null to skip checking the expected options
   * @param pattern         a regexp which should match the wanted option
   */
  public ChooserInterceptor(@Nullable List<String> expectedOptions, @NotNull @RegExp String pattern) {
    super(JBPopup.class);
    myOptions = expectedOptions;
    myToSelect = Pattern.compile(pattern);
  }

  public ChooserInterceptor(@Nullable List<String> expectedOptions,
                            @NotNull @RegExp String pattern,
                            @NotNull Function<List<String>, String> chooseOption) {
    this(expectedOptions, pattern);
    myChooseOption = chooseOption;
  }

  @Override
  protected void doIntercept(@NotNull JBPopup popup) {
    JBList<?> content = popup.isDisposed() ? null : UIUtil.findComponentOfType(popup.getContent(), JBList.class);
    if (content == null) {
      fail("JBList not found under " + popup.getContent());
    }
    ListModel<?> model = content.getModel();
    List<String> actualOptions = IntStream.range(0, model.getSize()).mapToObj(model::getElementAt).map(Object::toString).collect(Collectors.toList());
    if (myOptions != null) {
      assertEquals(myOptions, actualOptions);
    }
    List<String> matched = ContainerUtil.filter(actualOptions, opt -> myToSelect.matcher(opt).matches());
    if (matched.isEmpty()) {
      fail("No option matches pattern " + myToSelect + " (available options: " + String.join(", ", actualOptions) + ")");
    }
    if (myChooseOption != null) {
      content.setSelectedIndex(actualOptions.indexOf(myChooseOption.apply(matched)));
    } else if (matched.size() == 1) {
      content.setSelectedIndex(actualOptions.indexOf(matched.get(0)));
    } else {
      fail("Several options matched: " + matched + " (pattern: " + myToSelect + ")");
    }
    assertTrue(popup.canClose()); // calls cancelHandler
    if (popup instanceof ListPopup listPopup) {
      listPopup.handleSelect(true);
    }
    popup.closeOk(null);
    if (!ApplicationManager.getApplication().isWriteAccessAllowed()) {
      NonBlockingReadActionImpl.waitForAsyncTaskCompletion();
    }
  }

  @Override
  public String toString() {
    return "Popup Chooser where '" + myToSelect + "' should be selected";
  }
}
