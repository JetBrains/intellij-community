// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui;

import com.intellij.openapi.ui.popup.JBPopup;
import com.intellij.ui.components.JBList;
import com.intellij.util.ui.UIUtil;
import one.util.streamex.IntStreamEx;
import one.util.streamex.StreamEx;
import org.intellij.lang.annotations.RegExp;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.util.List;
import java.util.regex.Pattern;

import static org.junit.Assert.*;

/**
 * Interceptor for the next popup chooser
 */
public class ChooserInterceptor extends UiInterceptors.UiInterceptor<JBPopup> {
  final List<String> myOptions;
  final Pattern myToSelect;

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

  @Override
  protected void doIntercept(JBPopup popup) {
    JBList<?> content = popup.isDisposed() ? null : UIUtil.findComponentOfType(popup.getContent(), JBList.class);
    if (content == null) {
      fail("JBList not found under " + popup.getContent());
    }
    ListModel<?> model = content.getModel();
    List<String> actualOptions = IntStreamEx.range(model.getSize()).mapToObj(model::getElementAt).map(Object::toString).toList();
    if (myOptions != null) {
      assertEquals(myOptions, actualOptions);
    }
    List<String> matched = StreamEx.of(actualOptions).filter(opt -> myToSelect.matcher(opt).matches()).toList();
    if (matched.isEmpty()) {
      fail("No option matches pattern " + myToSelect + " (available options: " + String.join(", ", actualOptions) + ")");
    }
    if (matched.size() > 1) {
      fail("Several options matched: " + matched + " (pattern: " + myToSelect + ")");
    }
    content.setSelectedIndex(actualOptions.indexOf(matched.get(0)));
    assertTrue(popup.canClose()); // calls cancelHandler
    popup.closeOk(null);
  }

  @Override
  public String toString() {
    return "Popup Chooser where '" + myToSelect + "' should be selected";
  }
}
